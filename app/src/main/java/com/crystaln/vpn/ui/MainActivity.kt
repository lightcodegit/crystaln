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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileImporter
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionRefresher
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.data.SubscriptionSyncScheduler
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
import kotlinx.coroutines.withContext

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
    private val urlDialog = mutableStateOf(false)
    private val wallpaperPath = mutableStateOf<String?>(null)
    // Live profile list + selection, refreshed after every import/edit/delete.
    private val profiles = mutableStateOf<List<Profile>>(emptyList())
    private val selectedId = mutableStateOf<String?>(null)

    // Subscription rows and their refresh progress.
    private val subscriptions = mutableStateOf<List<Subscription>>(emptyList())
    private val subscriptionsBusy = mutableStateOf(false)
    private val subscriptionBusyId = mutableStateOf<String?>(null)

    /** Measured TCP ping per profile id (ms), -1 = unreachable. */
    private val pingResults = mutableStateMapOf<String, Long>()
    private val pinging: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    /** Auto-connect must only trigger on a cold start, not on a rotation. */
    private var freshLaunch: Boolean = true

    private fun refreshProfiles() {
        profiles.value = ProfileStore.load(this).toList()
        selectedId.value = ProfileStore.selectedId
    }

    private fun refreshSubscriptions() {
        subscriptions.value = SubscriptionsStore.load(this)
    }

    private fun refreshWallpaper() {
        wallpaperPath.value = SettingsStore.wallpaper(this)
    }

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingProfileId
            pendingProfileId = null
            if (result.resultCode == Activity.RESULT_OK && id != null) {
                ProfileStore.load(this@MainActivity).firstOrNull { it.id == id }?.let { launchConnect(it) }
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val qrScan =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringExtra(ScannerActivity.EXTRA_TEXT)?.let { importText(it) }
        }

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
                    ProfileStore.load(this@MainActivity).toList(), ProfileStore.selectedId,
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
                SettingsStore.setWallpaper(this@MainActivity, WALLPAPER_FILE)
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
        refreshSubscriptions()
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
                // (The 1 Hz poller lives in VpnManager now.)

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

                AnimatedContent(
                    targetState = nav.value,
                    transitionSpec = {
                        // Directional slide: screens ordered MAIN →
                        // SETTINGS → EDITOR; going "forward" slides left.
                        val forward = targetState.ordinal > initialState.ordinal
                        (
                            slideInHorizontally(
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                            ) { full -> if (forward) full / 3 else -full / 3 } +
                                fadeIn(tween(300))
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                            ) { full -> if (forward) -full / 3 else full / 3 } +
                                fadeOut(tween(220))
                            )
                    },
                    label = "nav",
                ) { screen ->
                    when (screen) {
                    Nav.MAIN -> MainScreen(
                        isDark = dark,
                        state = uiState,
                        profiles = profiles.value,
                        selectedId = selectedId.value,
                        wallpaperPath = wallpaperPath.value,
                        onSelect = { p ->
                            ProfileStore.select(this@MainActivity, p.id)
                            refreshProfiles()
                        },
                        onEdit = { p ->
                            editing.value = p
                            nav.value = Nav.EDITOR
                        },
                        onDelete = { p ->
                            // Stop the tunnel first if this profile is live.
                            if (VpnManager.current().profile?.id == p.id) {
                                VpnManager.stop(this@MainActivity)
                            }
                            ProfileStore.remove(this@MainActivity, p.id)
                            refreshProfiles()
                        },
                        onAdd = {
                            editing.value = null
                            nav.value = Nav.EDITOR
                        },
                        onImport = { importMenu.value = true },
                        onConnect = { onConnectClicked() },
                        onSettings = { nav.value = Nav.SETTINGS },
                        pingResults = pingResults,
                        onPing = { p -> pingProfile(p) },
                    )

                    Nav.SETTINGS -> SettingsScreen(
                        isDark = dark,
                        themeMode = themeMode.value,
                        onThemeChange = { mode ->
                            themeMode.value = mode
                            SettingsStore.setThemeMode(this@MainActivity, mode)
                        },
                        dynamicColor = dynamicColor.value,
                        onDynamicColorChange = { on ->
                            dynamicColor.value = on
                            SettingsStore.setDynamicColor(this@MainActivity, on)
                        },
                        paletteId = paletteId.value,
                        onPaletteChange = { id ->
                            paletteId.value = id
                            SettingsStore.setPaletteId(this@MainActivity, id)
                        },
                        autoConnect = autoConnect.value,
                        onAutoConnectChange = { on ->
                            autoConnect.value = on
                            SettingsStore.setAutoConnect(this@MainActivity, on)
                        },
                        subscriptions = subscriptions.value,
                        subscriptionsBusy = subscriptionsBusy.value,
                        subscriptionBusyId = subscriptionBusyId.value,
                        onAddSubscription = { url -> addSubscription(url) },
                        onRefreshSubscription = { sub -> refreshSubscription(sub.id) },
                        onRefreshAllSubscriptions = { refreshAllSubscriptions() },
                        onToggleSubscription = { sub, on -> toggleSubscription(sub, on) },
                        onRemoveSubscription = { sub -> removeSubscription(sub) },
                        wallpaperSet = wallpaperPath.value != null,
                        onPickWallpaper = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onClearWallpaper = {
                            File(filesDir, WALLPAPER_FILE).delete()
                            SettingsStore.setWallpaper(this@MainActivity, null)
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
                        isDark = dark,
                        onSave = { p ->
                            ProfileStore.upsert(this@MainActivity, p)
                            refreshProfiles()
                            nav.value = Nav.MAIN
                        },
                        onBack = { nav.value = Nav.MAIN },
                    )
                    }
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
                        onQr = {
                            importMenu.value = false
                            qrScan.launch(Intent(this, ScannerActivity::class.java))
                        },
                        onUrl = {
                            importMenu.value = false
                            urlDialog.value = true
                        },
                    )
                }

                if (urlDialog.value) {
                    UrlImportDialog(
                        onDismiss = { urlDialog.value = false },
                        onOk = { url ->
                            urlDialog.value = false
                            importFromUrl(url)
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
        VpnManager.connect(this@MainActivity, profile)
    }

    /** Measures the profile's server reachability on demand (TCP connect). */
    private fun pingProfile(profile: Profile) {
        if (!pinging.add(profile.id)) return
        lifecycleScope.launch(Dispatchers.IO) {
            val ms = tcpPing(profile.address, profile.port)
            pingResults[profile.id] = ms
            pinging.remove(profile.id)
        }
    }

    private fun tcpPing(host: String, port: Int): Long = try {
        val started = System.nanoTime()
        java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress(host, port), 5000)
        }
        (System.nanoTime() - started) / 1_000_000
    } catch (_: Throwable) {
        -1L
    }

    private fun importClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        importText(text)
    }

    /** Downloads a config from a URL (subscriptions are plain text too). */
    private fun importFromUrl(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val text = try {
                downloadText(url)
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.import_url_failed, Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) { importText(text) }
        }
    }

    /** Adds and immediately refreshes a saved subscription. */
    private fun addSubscription(rawUrl: String) {
        val url = SubscriptionsStore.normalizeUrl(rawUrl)
        if (url == null) {
            Toast.makeText(this, R.string.subscription_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        if (subscriptionsBusy.value) return
        lifecycleScope.launch(Dispatchers.IO) {
            subscriptionsBusy.value = true
            try {
                val sub = SubscriptionsStore.add(this@MainActivity, url)
                SubscriptionSyncScheduler.reschedule(this@MainActivity)
                subscriptionBusyId.value = sub.id
                val result = SubscriptionRefresher.refresh(this@MainActivity, sub)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    Toast.makeText(
                        this@MainActivity,
                        if (result.ok) {
                            getString(
                                R.string.subscription_added_ok,
                                result.added + result.updated,
                            )
                        } else {
                            getString(R.string.subscription_added_empty)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                subscriptionBusyId.value = null
                subscriptionsBusy.value = false
            }
        }
    }

    private fun refreshSubscription(id: String) {
        if (subscriptionsBusy.value) return
        val sub = SubscriptionsStore.byId(this, id) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            subscriptionsBusy.value = true
            subscriptionBusyId.value = id
            try {
                val result = SubscriptionRefresher.refresh(this@MainActivity, sub)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    Toast.makeText(
                        this@MainActivity,
                        if (result.ok) {
                            getString(
                                R.string.subscription_refresh_ok,
                                result.added,
                                result.updated,
                                result.removed,
                            )
                        } else {
                            getString(R.string.subscription_refresh_failed)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                subscriptionBusyId.value = null
                subscriptionsBusy.value = false
            }
        }
    }

    private fun refreshAllSubscriptions() {
        if (subscriptionsBusy.value) return
        lifecycleScope.launch(Dispatchers.IO) {
            subscriptionsBusy.value = true
            try {
                val results = SubscriptionRefresher.refreshAll(this@MainActivity)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    val failed = results.count { !it.second.ok }
                    val message = if (failed == 0) {
                        getString(R.string.subscription_refresh_ok, 0, 0, 0)
                    } else {
                        getString(R.string.subscription_refresh_failed)
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                subscriptionsBusy.value = false
            }
        }
    }

    private fun toggleSubscription(sub: Subscription, enabled: Boolean) {
        SubscriptionsStore.update(this, sub.copy(enabled = enabled))
        SubscriptionSyncScheduler.reschedule(this)
        refreshSubscriptions()
        if (enabled) refreshSubscription(sub.id)
    }

    private fun removeSubscription(sub: Subscription) {
        SubscriptionsStore.remove(this, sub.id)
        ProfileStore.removeBySubscription(this, sub.id)
        SubscriptionSyncScheduler.reschedule(this)
        refreshProfiles()
        refreshSubscriptions()
        Toast.makeText(this, R.string.subscription_removed, Toast.LENGTH_SHORT).show()
    }

    private fun downloadText(url: String): String {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.use { input ->
                val bytes = input.readLimited(maxImportBytes)
                if (bytes.size > maxImportBytes) {
                    throw java.io.IOException(getString(R.string.import_too_large))
                }
                return String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
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
        // Re-imports update the same server instead of stacking duplicates.
        val merged = ProfileStore.mergeProfiles(this, parsed, subscriptionId = null, selectFirst = true)
        refreshProfiles()
        Toast.makeText(
            this,
            getString(R.string.import_result_ok, merged.added + merged.updated) + " · " +
                getString(R.string.import_selected, merged.selectedProfile?.name ?: ""),
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
    onQr: () -> Unit,
    onUrl: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text(stringResource(R.string.profiles_title)) },
        text = {
            Column {
                ImportAction(Icons.Default.FileOpen, stringResource(R.string.import_file_action), onFile)
                ImportAction(Icons.Default.ContentPaste, stringResource(R.string.import_clipboard), onClipboard)
                ImportAction(Icons.Default.QrCodeScanner, stringResource(R.string.import_qr), onQr)
                ImportAction(Icons.Default.Link, stringResource(R.string.import_url), onUrl)
                ImportAction(Icons.Default.FileDownload, stringResource(R.string.export_action), onExport)
            }
        },
    )
}

@Composable
private fun UrlImportDialog(
    onDismiss: () -> Unit,
    onOk: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_url)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.import_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = url.trim().startsWith("http://") || url.trim().startsWith("https://"),
                onClick = { onOk(url.trim()) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
