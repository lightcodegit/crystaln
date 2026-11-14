package com.crystaln.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileImporter
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.ui.screens.EditorScreen
import com.crystaln.vpn.ui.screens.MainScreen
import com.crystaln.vpn.ui.screens.SettingsScreen
import com.crystaln.vpn.ui.theme.CrystalNTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null

    /** Max size of an imported config, protects against OOM from a hostile URI. */
    private val maxImportBytes = 512 * 1024

    /** Max size of a wallpaper image copied into internal storage. */
    private val maxWallpaperBytes = 25L * 1024 * 1024

    // Simple in-process navigation state.
    private enum class Nav { MAIN, SETTINGS, EDITOR }

    private val nav = mutableStateOf(Nav.MAIN)
    private val editing = mutableStateOf<Profile?>(null)
    private val themeMode = mutableStateOf(SettingsStore.ThemeMode.SYSTEM)
    private val dynamicColor = mutableStateOf(false)
    private val paletteId = mutableStateOf("indigo")
    private val autoConnect = mutableStateOf(false)
    private val importMenu = mutableStateOf(false)
    private val wallpaperPath = mutableStateOf<String?>(null)
    // Live profile list + selection, refreshed after every import/edit/delete.
    private val profiles = mutableStateOf<List<Profile>>(emptyList())
    private val selectedId = mutableStateOf<String?>(null)

    /** Auto-connect must only trigger on a cold start, not on a rotation. */
    private var freshLaunch: Boolean = true

    private fun refreshProfiles() {
        profiles.value = ProfileStore.load(this).toList()
        selectedId.value = ProfileStore.selectedId
    }

    private fun refreshWallpaper() {
        wallpaperPath.value = SettingsStore.wallpaper(this)
    }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingProfileId
            pendingProfileId = null
            if (result.resultCode == Activity.RESULT_OK && id != null) {
                ProfileStore.load(this).firstOrNull { it.id == id }?.let { launchConnect(it) }
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val fileImport =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            readTextFromUri(uri)?.let { importText(it) }
        }

    private val fileExport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val json = ProfileImporter.exportJson(
                    ProfileStore.load(this).toList(), ProfileStore.selectedId,
                )
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
            }
        }

    private val wallpaperPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val dst = File(filesDir, WALLPAPER_FILE)
                contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { output ->
                        val copied = input.copyLimitedTo(output, maxWallpaperBytes)
                        if (copied > maxWallpaperBytes) {
                            dst.delete()
                            Toast.makeText(this, R.string.wallpaper_too_large, Toast.LENGTH_LONG).show()
                            return@registerForActivityResult
                        }
                    }
                } ?: return@registerForActivityResult
                SettingsStore.setWallpaper(this, WALLPAPER_FILE)
                refreshWallpaper()
            } catch (t: Throwable) {
                Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A saved instance state means this is a recreation (rotation, theme
        // switch) — auto-connect must not re-fire against the user's intent.
        freshLaunch = savedInstanceState == null
        themeMode.value = SettingsStore.themeMode(this)
        dynamicColor.value = SettingsStore.dynamicColor(this)
        paletteId.value = SettingsStore.paletteId(this)
        autoConnect.value = SettingsStore.autoConnect(this)
        refreshProfiles()
        refreshWallpaper()

        handleViewIntent(intent)

        setContent {
            val dark = when (themeMode.value) {
                SettingsStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
                SettingsStore.ThemeMode.LIGHT -> false
                SettingsStore.ThemeMode.DARK -> true
            }
            CrystalNTheme(
                darkTheme = dark,
                dynamicColor = dynamicColor.value,
                paletteId = paletteId.value,
            ) {
                val uiState by VpnManager.ui.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Live traffic counters while connected.
                LaunchedEffect(Unit) {
                    launch(Dispatchers.IO) {
                        while (isActive) {
                            if (VpnManager.current().state == VpnManager.State.CONNECTED) {
                                val (rx, tx) = VpnManager.pollTraffic()
                                VpnManager.notifyTraffic(rx, tx)
                            }
                            delay(1000)
                        }
                    }
                }

                // Auto-connect the selected profile on cold launch, if enabled.
                if (freshLaunch) {
                    LaunchedEffect(Unit) {
                        if (SettingsStore.autoConnect(this@MainActivity) &&
                            VpnManager.current().state == VpnManager.State.OFF
                        ) {
                            val p = ProfileStore.selected(this@MainActivity)
                                ?: ProfileStore.load(this@MainActivity).firstOrNull()
                            if (p != null) {
                                ProfileStore.select(this@MainActivity, p.id)
                                refreshProfiles()
                                onConnectClicked()
                            }
                        }
                    }
                }

                when (nav.value) {
                    Nav.MAIN -> MainScreen(
                        isDark = dark,
                        state = uiState,
                        profiles = profiles.value,
                        selectedId = selectedId.value,
                        wallpaperPath = wallpaperPath.value,
                        onSelect = { p ->
                            ProfileStore.select(this, p.id)
                            refreshProfiles()
                        },
                        onEdit = { p ->
                            editing.value = p
                            nav.value = Nav.EDITOR
                        },
                        onDelete = { p ->
                            // Stop the tunnel first if this profile is live.
                            if (VpnManager.current().profile?.id == p.id) {
                                VpnManager.stop(this)
                            }
                            ProfileStore.remove(this, p.id)
                            refreshProfiles()
                        },
                        onAdd = {
                            editing.value = null
                            nav.value = Nav.EDITOR
                        },
                        onImport = { importMenu.value = true },
                        onConnect = { onConnectClicked() },
                        onSettings = { nav.value = Nav.SETTINGS },
                    )

                    Nav.SETTINGS -> SettingsScreen(
                        isDark = dark,
                        themeMode = themeMode.value,
                        onThemeChange = { mode ->
                            themeMode.value = mode
                            SettingsStore.setThemeMode(this, mode)
                        },
                        dynamicColor = dynamicColor.value,
                        onDynamicColorChange = { on ->
                            dynamicColor.value = on
                            SettingsStore.setDynamicColor(this, on)
                        },
                        paletteId = paletteId.value,
                        onPaletteChange = { id ->
                            paletteId.value = id
                            SettingsStore.setPaletteId(this, id)
                        },
                        autoConnect = autoConnect.value,
                        onAutoConnectChange = { on ->
                            autoConnect.value = on
                            SettingsStore.setAutoConnect(this, on)
                        },
                        wallpaperSet = wallpaperPath.value != null,
                        onPickWallpaper = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onClearWallpaper = {
                            File(filesDir, WALLPAPER_FILE).delete()
                            SettingsStore.setWallpaper(this, null)
                            refreshWallpaper()
                        },
                        onImport = {
                            importMenu.value = false
                            fileImport.launch(arrayOf("*/*"))
                        },
                        onExport = {
                            importMenu.value = false
                            fileExport.launch("crystaln_profiles.json")
                        },
                        onBack = { nav.value = Nav.MAIN },
                    )

                    Nav.EDITOR -> EditorScreen(
                        existing = editing.value,
                        onSave = { p ->
                            ProfileStore.upsert(this, p)
                            refreshProfiles()
                            nav.value = Nav.MAIN
                        },
                        onBack = { nav.value = Nav.MAIN },
                    )
                }

                if (importMenu.value) {
                    ImportDialog(
                        onDismiss = { importMenu.value = false },
                        onClipboard = {
                            importMenu.value = false
                            importClipboard()
                        },
                        onFile = {
                            importMenu.value = false
                            fileImport.launch(arrayOf("*/*"))
                        },
                        onExport = {
                            importMenu.value = false
                            fileExport.launch("crystaln_profiles.json")
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** Opening a config file from a file manager (VIEW intent). */
    private fun handleViewIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        readTextFromUri(uri)?.let { importText(it) }
    }

    private fun readTextFromUri(uri: android.net.Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readLimited(maxImportBytes)
            if (bytes.size > maxImportBytes) {
                Toast.makeText(this, R.string.import_too_large, Toast.LENGTH_LONG).show()
                null
            } else {
                String(bytes, Charsets.UTF_8)
            }
        }
    } catch (t: Throwable) {
        Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
        null
    }

    private fun onConnectClicked() {
        when (VpnManager.current().state) {
            VpnManager.State.OFF -> {
                val p = ProfileStore.selected(this) ?: run {
                    Toast.makeText(this, R.string.no_profile, Toast.LENGTH_SHORT).show()
                    return
                }
                pendingProfileId = p.id
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermission.launch(intent)
                } else {
                    launchConnect(p)
                }
            }

            else -> VpnManager.stop(this)
        }
    }

    private fun launchConnect(profile: Profile) {
        VpnManager.connect(this, profile)
    }

    private fun importClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        importText(text)
    }

    private fun importText(text: String) {
        if (text.length > maxImportBytes) {
            Toast.makeText(this, R.string.import_too_large, Toast.LENGTH_LONG).show()
            return
        }
        val parsed = ProfileImporter.parseText(text)
        if (parsed.isEmpty()) {
            Toast.makeText(this, R.string.import_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        parsed.forEach { ProfileStore.upsert(this, it) }
        // Install-and-select: the first imported profile becomes active at once.
        ProfileStore.select(this, parsed.first().id)
        refreshProfiles()
        Toast.makeText(
            this,
            getString(R.string.import_result_ok, parsed.size) + " · " +
                getString(R.string.import_selected, parsed.first().name),
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val WALLPAPER_FILE = "wallpaper.jpg"
    }
}

/** Reads at most [limit]+1 bytes so oversized inputs fail instead of OOM-ing. */
private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n <= 0) break
        total += n
        if (total > limit) {
            out.write(buf, 0, n)
            break
        }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** Copies up to [limit]+1 bytes, returning the actual copied count. */
private fun java.io.InputStream.copyLimitedTo(
    output: java.io.OutputStream,
    limit: Long,
): Long {
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n <= 0) break
        total += n
        output.write(buf, 0, n)
        if (total > limit) break
    }
    return total
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onFile: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text("Профили") },
        text = {
            Column {
                ImportAction(Icons.Default.FileOpen, "Установить конфиг из файла", onFile)
                ImportAction(Icons.Default.ContentPaste, "Импорт из буфера", onClipboard)
                ImportAction(Icons.Default.FileDownload, "Экспорт в файл", onExport)
            }
        },
    )
}

@Composable
private fun ImportAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(label)
        }
    }
}
