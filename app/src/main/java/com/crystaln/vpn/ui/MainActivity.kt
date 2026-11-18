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
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Lock
import androidx.fragment.app.FragmentActivity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
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
import com.crystaln.vpn.data.Backup
import com.crystaln.vpn.data.ProfileImporter
import com.crystaln.vpn.data.ProfileVault
import com.crystaln.vpn.data.ConnectionDiagnostics
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.TrafficStats
import com.crystaln.vpn.data.TrafficStatsStore
import com.crystaln.vpn.data.UpdateChecker
import com.crystaln.vpn.data.GroupStore
import com.crystaln.vpn.net.TcpPing
import com.crystaln.vpn.ui.ServerAlias
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionRefresher
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.data.SubscriptionSyncScheduler
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.widget.CrystalListWidget
import com.crystaln.vpn.ui.screens.DiagnosticsScreen
import com.crystaln.vpn.ui.screens.EditorScreen
import com.crystaln.vpn.ui.screens.OnboardingScreen
import com.crystaln.vpn.ui.screens.CheckSummary
import com.crystaln.vpn.ui.screens.MainScreen
import com.crystaln.vpn.ui.screens.ProfilesScreen
import com.crystaln.vpn.ui.screens.SettingsScreen
import com.crystaln.vpn.ui.screens.SplitTunnelScreen
import com.crystaln.vpn.ui.screens.ToolsScreen
import com.crystaln.vpn.ui.theme.CrystalNTheme
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    /** v2fly domain list for the ad-block routing rule (dlc.dat = geosite.dat). */
    private val geositeUrl =
        "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat"

    private var pendingProfileId: String? = null

    /** Max size of an imported config, protects against OOM from a hostile URI. */
    private val maxImportBytes = 512 * 1024

    /** Max size of a wallpaper image copied into internal storage. */
    private val maxWallpaperBytes = 25L * 1024 * 1024

    // Simple in-process navigation state.
    private enum class Nav { MAIN, PROFILES, SETTINGS, TOOLS, DIAGNOSTICS, EDITOR, ONBOARDING }

    private val nav = mutableStateOf(Nav.MAIN)
    /** Back stack for the hardware/gesture back button. */
    private val navHistory = mutableListOf<Nav>()

    private fun goto(target: Nav) {
        if (nav.value == target) return
        navHistory.add(nav.value)
        nav.value = target
    }

    private fun goBack() {
        nav.value = navHistory.removeLastOrNull() ?: Nav.MAIN
    }
    private val editing = mutableStateOf<Profile?>(null)
    private val themeMode = mutableStateOf(SettingsStore.ThemeMode.SYSTEM)
    private val dynamicColor = mutableStateOf(false)
    private val paletteId = mutableStateOf("indigo")
    private val gyroBackground = mutableStateOf(false)
    private val amoledMode = mutableStateOf(false)
    private val appLock = mutableStateOf(false)
    /** True while the biometric lock is armed and not yet passed. */
    private val locked = mutableStateOf(false)
    private val liquidBackground = mutableStateOf(false)
    private val anonymizeNames = mutableStateOf(false)
    private val exportSettingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(SettingsStore.exportJson(this).toString(2).toByteArray())
            }
            Toast.makeText(this, "Настройки экспортированы", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
        }
    }
    private val importSettingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@registerForActivityResult
            val o = org.json.JSONObject(text)
            SettingsStore.importJson(this, o)
            Toast.makeText(this, "Настройки импортированы", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Ошибка импорта", Toast.LENGTH_SHORT).show()
        }
    }
    private val splitTunnelVisible = mutableStateOf(false)
    private val autoReconnect = mutableStateOf(false)
    private val fallbackProfileId = mutableStateOf<String?>(null)
    private val trafficLimitMb = mutableStateOf(0)
    private val killSwitch = mutableStateOf(false)
    private val checkUrlText = mutableStateOf(SettingsStore.DEFAULT_CHECK_URL)
    private val checkTimeoutSec = mutableStateOf(15)
    private val autoConnect = mutableStateOf(false)
    private val autoFastest = mutableStateOf(false)
    private val dnsViaTunnel = mutableStateOf(false)
    private val dnsServer = mutableStateOf("1.1.1.1")
    private val blockAds = mutableStateOf(false)
    private val blockAdsBusy = mutableStateOf(false)
    private val importMenu = mutableStateOf(false)
    private val urlDialog = mutableStateOf(false)
    private val wallpaperPath = mutableStateOf<String?>(null)
    // Live profile list + selection, refreshed after every import/edit/delete.
    private val profiles = mutableStateOf<List<Profile>>(emptyList())
    private val selectedId = mutableStateOf<String?>(null)
    private val profileStats = mutableStateOf<Map<String, TrafficStats>>(emptyMap())
    private val searchQuery = mutableStateOf("")
    private val shareProfile = mutableStateOf<Profile?>(null)

    // First-run wizard step (0..2).
    private val onboardingStep = mutableStateOf(0)

    // New GitHub release detected at startup (null = up to date / offline).
    private val updateRelease = mutableStateOf<UpdateChecker.Release?>(null)

    // Subscription rows and their refresh progress.
    private val subscriptions = mutableStateOf<List<Subscription>>(emptyList())
    private val subscriptionsBusy = mutableStateOf(false)
    private val subscriptionBusyId = mutableStateOf<String?>(null)

    /** Measured TCP ping per profile id (ms), -1 = unreachable. */
    private val pingResults = mutableStateMapOf<String, Long>()
    private val pingAllRunning = mutableStateOf(false)
    /** Full-availability-check progress: done, total (0 = idle). */
    private val pingAllProgress = mutableStateOf(0 to 0)
    /** Aggregated result of the last full availability check (ui.screens.CheckSummary). */
    private val pingAllSummary = mutableStateOf<CheckSummary?>(null)

    /** User-defined profile groups (canonical name list). */
    private val groups = mutableStateOf<List<String>>(emptyList())

    private fun refreshGroups() {
        lifecycleScope.launch(Dispatchers.IO) {
            val loaded = GroupStore.load(this@MainActivity)
            withContext(Dispatchers.Main) { groups.value = loaded }
        }
    }

    // Connection diagnostics: null = not run yet, list = last run's results.
    private val diagResults = mutableStateOf<List<ConnectionDiagnostics.Result>?>(null)
    private val diagRunning = mutableStateOf(false)

    // Protected export: password chosen in ExportDialog, consumed by fileExport.
    private var pendingExportPassword: String? = null
    private val exportDialogVisible = mutableStateOf(false)

    // Import of a vault envelope: text held until the user enters the password.
    private val vaultPendingText = mutableStateOf<String?>(null)
    private val pinging: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    /** Auto-connect must only trigger on a cold start, not on a rotation. */
    private var freshLaunch: Boolean = true

    private fun refreshProfiles() {
        profiles.value = ProfileStore.load(this).toList()
        selectedId.value = ProfileStore.selectedId
        profileStats.value = TrafficStatsStore.all(this)
        // The 4x2 list widget re-reads the store when its data changes.
        try {
            CrystalListWidget.notifyDataChanged(this)
        } catch (_: Throwable) {
        }
    }

    private fun refreshSubscriptions() {
        subscriptions.value = SubscriptionsStore.load(this)
    }

    private fun refreshWallpaper() {
        wallpaperPath.value = SettingsStore.wallpaper(this)
    }

    /** Ends the first-run wizard: any successful import or "skip" counts. */
    private fun completeOnboarding() {
        if (!SettingsStore.onboarded(this)) {
            SettingsStore.setOnboarded(this, true)
            if (nav.value == Nav.ONBOARDING) {
                navHistory.clear()
                nav.value = Nav.MAIN
            }
        }
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
                var json = Backup.exportFull(this@MainActivity)
                // Password set by ExportDialog → write a vault envelope instead
                // of raw credentials.
                pendingExportPassword?.let { pw ->
                    json = ProfileVault.encrypt(json, pw.toCharArray())
                }
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
            } finally {
                pendingExportPassword = null
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
        gyroBackground.value = SettingsStore.gyroBackground(this)
        amoledMode.value = SettingsStore.amoledMode(this)
        appLock.value = SettingsStore.appLock(this)
        locked.value = appLock.value
        liquidBackground.value = SettingsStore.liquidBackground(this)
        lifecycleScope.launch {
            VpnManager.limitReached.collect { reached ->
                if (reached) {
                    val t = Toast.makeText(this@MainActivity, R.string.traffic_limit_reached, Toast.LENGTH_LONG)
                    t.show()
                }
            }
        }
        anonymizeNames.value = SettingsStore.anonymizeNames(this)
        autoReconnect.value = SettingsStore.autoReconnect(this)
        fallbackProfileId.value = SettingsStore.fallbackProfileId(this)
        trafficLimitMb.value = SettingsStore.trafficLimitMb(this)
        killSwitch.value = SettingsStore.killSwitch(this)
        checkUrlText.value = SettingsStore.checkUrl(this)
        checkUrlText.value = SettingsStore.checkUrl(this)
        checkTimeoutSec.value = SettingsStore.checkTimeoutSec(this)
        autoConnect.value = SettingsStore.autoConnect(this)
        autoFastest.value = SettingsStore.autoFastest(this)
        dnsViaTunnel.value = SettingsStore.dnsViaTunnel(this)
        dnsServer.value = SettingsStore.dnsServer(this)
        blockAds.value = SettingsStore.blockAds(this)
        refreshProfiles()
        refreshSubscriptions()
        refreshWallpaper()
        refreshGroups()
        if (!SettingsStore.onboarded(this)) {
            navHistory.clear()
            nav.value = Nav.ONBOARDING
        }

        // Hardware/gesture back walks the in-app history instead of leaving.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (nav.value) {
                    Nav.MAIN, Nav.ONBOARDING -> finish()
                    else -> goBack()
                }
            }
        })

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
                amoledMode = amoledMode.value,
            ) {
                if (appLock.value && locked.value) {
                    LockScreen(onUnlock = { showBiometricPrompt() })
                    return@CrystalNTheme
                }
                CompositionLocalProvider(
                    LocalGyroBackground provides gyroBackground.value,
                    LocalLiquidBackground provides liquidBackground.value,
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
                // Refresh per-profile usage totals every ~10s while it runs.
                LaunchedEffect(Unit) {
                    var ticks = 0
                    VpnManager.ui.collect {
                        if (++ticks % 10 == 0 && VpnManager.isRunning()) {
                            profileStats.value = TrafficStatsStore.all(this@MainActivity)
                        }
                    }
                }

                // One-time update check against GitHub releases (best-effort).
                LaunchedEffect(Unit) {
                    if (!freshLaunch) return@LaunchedEffect
                    lifecycleScope.launch(Dispatchers.IO) {
                        val current = try {
                            packageManager
                                .getPackageInfo(packageName, 0).versionName.orEmpty()
                        } catch (_: Throwable) {
                            ""
                        }
                        val release = UpdateChecker.check(current)
                        if (release != null) {
                            withContext(Dispatchers.Main) { updateRelease.value = release }
                        }
                    }
                }

                // Auto-connect the selected profile on cold launch, if enabled.
                if (freshLaunch) {
                    LaunchedEffect(Unit) {
                        if (SettingsStore.autoConnect(this@MainActivity) &&
                            VpnManager.current().state == VpnManager.State.OFF
                        ) {
                            // Auto-select: measure first, take the fastest live server.
                            if (SettingsStore.autoFastest(this@MainActivity)) {
                                pingAll()
                                while (pingAllRunning.value) delay(100)
                            }
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
                        // PROFILES → SETTINGS; going "forward" slides left.
                        // A slightly larger travel distance with longer,
                        // softly-eased tweens and a gentle fade reads as a
                        // smooth glass panel gliding, not a janky jump.
                        val forward = targetState.ordinal > initialState.ordinal
                        (
                            slideInHorizontally(
                                animationSpec = tween(
                                    420,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) { full -> if (forward) full / 2 else -full / 2 } +
                                fadeIn(tween(300))
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(
                                    360,
                                    easing = FastOutSlowInEasing,
                                ),
                            ) { full -> if (forward) -full / 2 else full / 2 } +
                                fadeOut(tween(260))
                            )
                    },
                    label = "nav",
                ) { screen ->
                    when (screen) {
                    Nav.ONBOARDING -> OnboardingScreen(
                        isDark = dark,
                        step = onboardingStep.value,
                        onStep = { onboardingStep.value = it },
                        onImportClipboard = {
                            completeOnboarding()
                            importClipboard()
                        },
                        onImportFile = {
                            completeOnboarding()
                            fileImport.launch(arrayOf("*/*"))
                        },
                        onImportQr = {
                            completeOnboarding()
                            qrScan.launch(Intent(this@MainActivity, ScannerActivity::class.java))
                        },
                        onImportUrl = {
                            completeOnboarding()
                            urlDialog.value = true
                        },
                        onFinish = { completeOnboarding() },
                    )

                    Nav.MAIN -> MainScreen(
                        isDark = dark,
                        state = uiState,
                        profileCount = profiles.value.size,
                        selected = profiles.value.firstOrNull {
                            it.id == (uiState.profile?.id ?: selectedId.value)
                        },
                        selectedLabel = profiles.value.firstOrNull {
                            it.id == (uiState.profile?.id ?: selectedId.value)
                        }?.let { ServerAlias.label(it, profiles.value.indexOf(it)) },
                        wallpaperPath = wallpaperPath.value,
                        rxHistory = VpnManager.rxHistory.collectAsStateWithLifecycle().value,
                        txHistory = VpnManager.txHistory.collectAsStateWithLifecycle().value,
                        onConnect = { onConnectClicked() },
                        onSettings = { goto(Nav.SETTINGS) },
                        onProfiles = { goto(Nav.PROFILES) },
                        onTools = { goto(Nav.TOOLS) },
                    )

                    Nav.PROFILES -> ProfilesScreen(
                        isDark = dark,
                        profiles = profiles.value,
                        selectedId = selectedId.value,
                        anonymizeNames = anonymizeNames.value,
                        wallpaperPath = wallpaperPath.value,
                        onSelect = { p ->
                            ProfileStore.select(this@MainActivity, p.id)
                            refreshProfiles()
                        },
                        onEdit = { p ->
                            editing.value = p
                            goto(Nav.EDITOR)
                        },
                        onToggleFavorite = { p ->
                            ProfileStore.upsert(this@MainActivity, p.copy(favorite = !p.favorite))
                            refreshProfiles()
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
                            goto(Nav.EDITOR)
                        },
                        onImport = { importMenu.value = true },
                        onBack = { goBack() },
                        onSettings = { goto(Nav.SETTINGS) },
                        onTools = { goto(Nav.TOOLS) },
                        pingResults = pingResults,
                        onPing = { p -> pingProfile(p) },
                        pingAllRunning = pingAllRunning.value,
                        pingAllProgress = pingAllProgress.value,
                        pingAllSummary = pingAllSummary.value,
                        onPingAll = { pingAll() },
                        trafficStats = profileStats.value,
                        searchQuery = searchQuery.value,
                        onSearchChange = { searchQuery.value = it },
                        subscriptionTitles = subscriptions.value.associate { it.id to it.name },
                        groups = groups.value,
                        onCreateGroup = { name ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = GroupStore.add(this@MainActivity, name)
                                refreshGroups()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (ok) R.string.group_created else R.string.group_exists,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        onDeleteGroup = { name ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                GroupStore.remove(this@MainActivity, name)
                                refreshGroups()
                                refreshProfiles()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, R.string.group_deleted, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onShare = { shareProfile.value = it },
                        shareProfile = shareProfile.value,
                        onDismissShare = { shareProfile.value = null },
                    )

                    Nav.SETTINGS -> if (splitTunnelVisible.value) {
                        SplitTunnelScreen(
                            isDark = dark,
                            mode = SettingsStore.splitMode(this@MainActivity),
                            selected = SettingsStore.splitApps(this@MainActivity),
                            onModeChange = { m ->
                                SettingsStore.setSplitMode(this@MainActivity, m)
                            },
                            onToggleApp = { pkg ->
                                val cur = SettingsStore.splitApps(this@MainActivity).toMutableSet()
                                if (pkg in cur) cur.remove(pkg) else cur.add(pkg)
                                SettingsStore.setSplitApps(this@MainActivity, cur)
                            },
                            onBack = { splitTunnelVisible.value = false },
                        )
                    } else {
                        SettingsScreen(
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
                        gyroBackground = gyroBackground.value,
                        onGyroBackgroundChange = { on ->
                            gyroBackground.value = on
                            SettingsStore.setGyroBackground(this@MainActivity, on)
                        },
                        liquidBackground = liquidBackground.value,
                        onLiquidBackgroundChange = { on ->
                            liquidBackground.value = on
                            SettingsStore.setLiquidBackground(this@MainActivity, on)
                        },
                        amoledMode = amoledMode.value,
                        onAmoledModeChange = { on ->
                            amoledMode.value = on
                            SettingsStore.setAmoledMode(this@MainActivity, on)
                        },
                        appLock = appLock.value,
                        onAppLockChange = { on ->
                            appLock.value = on
                            SettingsStore.setAppLock(this@MainActivity, on)
                        },
                        autoConnect = autoConnect.value,
                        onAutoConnectChange = { on ->
                            autoConnect.value = on
                            SettingsStore.setAutoConnect(this@MainActivity, on)
                        },
                        autoFastest = autoFastest.value,
                        onAutoFastestChange = { on ->
                            autoFastest.value = on
                            SettingsStore.setAutoFastest(this@MainActivity, on)
                        },
                        dnsViaTunnel = dnsViaTunnel.value,
                        onDnsViaTunnelChange = { on ->
                            dnsViaTunnel.value = on
                            SettingsStore.setDnsViaTunnel(this@MainActivity, on)
                        },
                        dnsServer = dnsServer.value,
                        onDnsServerChange = { value ->
                            dnsServer.value = value
                            SettingsStore.setDnsServer(this@MainActivity, value)
                        },
                        blockAds = blockAds.value,
                        blockAdsBusy = blockAdsBusy.value,
                        onBlockAdsChange = { on -> toggleBlockAds(on) },
                        anonymizeNames = anonymizeNames.value,
                        onAnonymizeNamesChange = { on ->
                            anonymizeNames.value = on
                            SettingsStore.setAnonymizeNames(this@MainActivity, on)
                        },
                        checkUrl = checkUrlText.value,
                        onCheckUrlChange = { url ->
                            checkUrlText.value = url
                            SettingsStore.setCheckUrl(this@MainActivity, url)
                        },
                        checkTimeoutSec = checkTimeoutSec.value,
                        onCheckTimeoutSecChange = { sec ->
                            checkTimeoutSec.value = sec.coerceIn(3, 60)
                            SettingsStore.setCheckTimeoutSec(this@MainActivity, sec)
                        },
                        autoReconnect = autoReconnect.value,
                        onAutoReconnectChange = { on ->
                            autoReconnect.value = on
                            SettingsStore.setAutoReconnect(this@MainActivity, on)
                        },
                        fallbackProfileId = fallbackProfileId.value,
                        onFallbackProfileIdChange = { id ->
                            fallbackProfileId.value = id
                            SettingsStore.setFallbackProfileId(this@MainActivity, id)
                        },
                        trafficLimitMb = trafficLimitMb.value,
                        onTrafficLimitMbChange = { mb ->
                            trafficLimitMb.value = mb
                            SettingsStore.setTrafficLimitMb(this@MainActivity, mb)
                        },
                        killSwitch = killSwitch.value,
                        onKillSwitchChange = { on ->
                            killSwitch.value = on
                            SettingsStore.setKillSwitch(this@MainActivity, on)
                        },
                        onSplitTunnel = { splitTunnelVisible.value = true },
                        onUrlTestGroup = { urlTestVisibleProfiles() },
                        onExportSettings = { exportSettingsLauncher.launch("crystaln_settings.json") },
                        onImportSettings = { importSettingsLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onProfiles = { goto(Nav.PROFILES) },
                        onTools = { goto(Nav.TOOLS) },
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
                        onDiagnostics = {
                            diagResults.value = null
                            goto(Nav.DIAGNOSTICS)
                        },
                        onBack = { goBack() },
                    )
                    }

                    Nav.DIAGNOSTICS -> DiagnosticsScreen(
                        isDark = dark,
                        profileName = diagProfile()?.name ?: "",
                        protocolName = diagProfile()?.protocol?.displayName ?: "",
                        addressPort = diagProfile()?.let { "${it.address}:${it.port}" } ?: "",
                        vpnRunning = uiState.state == VpnManager.State.CONNECTED,
                        results = diagResults.value,
                        running = diagRunning.value,
                        onRun = { runDiagnostics() },
                        onBack = { goBack() },
                    )

                    Nav.TOOLS -> ToolsScreen(
                        isDark = dark,
                        killSwitch = killSwitch.value,
                        onKillSwitchChange = { on ->
                            killSwitch.value = on
                            SettingsStore.setKillSwitch(this@MainActivity, on)
                        },
                        onSplitTunnel = { splitTunnelVisible.value = true },
                        onDiagnostics = {
                            diagResults.value = null
                            goto(Nav.DIAGNOSTICS)
                        },
                        onUrlTest = { urlTestVisibleProfiles() },
                    )

                    Nav.EDITOR -> EditorScreen(
                        existing = editing.value,
                        isDark = dark,
                        groups = groups.value,
                        onSave = { p ->
                            ProfileStore.upsert(this@MainActivity, p)
                            refreshProfiles()
                            navHistory.clear()
                            nav.value = Nav.MAIN
                        },
                        onBack = { goBack() },
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
                            exportDialogVisible.value = true
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

                if (exportDialogVisible.value) {
                    ExportDialog(
                        onDismiss = { exportDialogVisible.value = false },
                        onExport = { password ->
                            exportDialogVisible.value = false
                            pendingExportPassword = password
                            fileExport.launch(
                                if (password == null) "crystaln_profiles.json" else "crystaln_vault.json",
                            )
                        },
                    )
                }

                if (vaultPendingText.value != null) {
                    VaultPasswordDialog(
                        onDismiss = { vaultPendingText.value = null },
                        onUnlock = { password ->
                            val envelope = vaultPendingText.value
                            vaultPendingText.value = null
                            if (envelope != null) decryptAndImport(envelope, password)
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

                if (updateRelease.value != null) {
                    val release = updateRelease.value
                    UpdateDialog(
                        release = release,
                        currentVersion = currentVersionName(),
                        onOpen = {
                            updateRelease.value = null
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release!!.url)),
                                )
                            } catch (t: Throwable) {
                                Toast.makeText(this, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
                            }
                        },
                        onDismiss = { updateRelease.value = null },
                    )
                }
                } // LocalGyroBackground
            }
        }
    }

    private fun currentVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Throwable) {
        ""
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Relock on every exit (home button, app switch, screen off).
        if (appLock.value) locked.value = true
    }

    /** Shows the system biometric dialog; success clears [locked]. */
    private fun showBiometricPrompt() {
        val manager = BiometricManager.from(this)
        val canAuth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            // No fingerprint enrolled — there is nothing to check against,
            // so do not hard-lock the user out of the app.
            locked.value = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    locked.value = false
                }
                // onAuthenticationError/Failed: stay locked, the LockScreen
                // keeps the "Unlock" button available.
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_title))
                .setSubtitle(getString(R.string.lock_sub))
                .setNegativeButtonText(getString(R.string.lock_cancel))
                .build(),
        )
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

    /** The profile the diagnostics screen is aimed at. */
    private fun diagProfile(): Profile? =
        ProfileStore.selected(this) ?: ProfileStore.load(this).firstOrNull()

    /**
     * Runs DNS/TCP/config checks off the UI thread. Safe to use while a VPN
     * is up: nothing here starts or stops a tunnel.
     */
    private fun runDiagnostics() {
        if (diagRunning.value) return
        val profile = diagProfile() ?: run {
            Toast.makeText(this, R.string.no_profile, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            diagRunning.value = true
            try {
                val results = ConnectionDiagnostics.run(profile)
                diagResults.value = results
            } finally {
                diagRunning.value = false
            }
        }
    }

    /** Measures the profile's server reachability on demand (TCP connect). */
    private fun pingProfile(profile: Profile) {
        if (!pinging.add(profile.id)) return
        lifecycleScope.launch(Dispatchers.IO) {
            val ms = when {
                profile.isHysteria -> com.crystaln.vpn.hysteria.HysteriaPing.measure(this@MainActivity, profile)
                profile.isSingBoxFamily -> com.crystaln.vpn.singbox.SingBoxPing.measure(this@MainActivity, profile)
                else -> tcpPing(profile.address, profile.port)
            }
            pingResults[profile.id] = ms
            pinging.remove(profile.id)
        }
    }

    /**
     * Full availability check: pings every profile in parallel (a few dozen
     * sockets are cheap), tracks done/total progress, and when auto-select is
     * enabled while the tunnel is down, moves the selection to the fastest
     * reachable server. The aggregated summary is shown on the profiles tab.
     */
    private fun pingAll() {
        if (pingAllRunning.value) return
        val list = ProfileStore.load(this)
        if (list.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            pingAllRunning.value = true
            pingAllSummary.value = null
            pingAllProgress.value = 0 to list.size
            pingResults.clear()
            try {
                val done = java.util.concurrent.atomic.AtomicInteger()
                coroutineScope {
                    list.forEach { p ->
                        launch {
                            val ms = tcpPing(p.address, p.port)
                            pingResults[p.id] = ms
                            pingAllProgress.value = done.incrementAndGet() to list.size
                        }
                    }
                }
                withContext(Dispatchers.Main) { maybeAutoSelectFastest() }
                // Aggregate: how many answered, and which profile was fastest.
                val live = list.filter { (pingResults[it.id] ?: -1L) >= 0 }
                val best = live.minByOrNull { pingResults[it.id] ?: Long.MAX_VALUE }
                pingAllSummary.value = CheckSummary(
                    reachable = live.size,
                    total = list.size,
                    bestMs = best?.let { pingResults[it.id] },
                    bestName = best?.name,
                )
            } finally {
                pingAllRunning.value = false
                pingAllProgress.value = 0 to 0
            }
        }
    }

    /** Picks the lowest-latency reachable profile when auto-select is on. */
    private fun maybeAutoSelectFastest() {
        if (!autoFastest.value) return
        if (VpnManager.isRunning()) return
        val best = profiles.value
            .filter { (pingResults[it.id] ?: -1L) >= 0 }
            .minByOrNull { pingResults[it.id] ?: Long.MAX_VALUE } ?: return
        if (best.id != selectedId.value) {
            ProfileStore.select(this, best.id)
            refreshProfiles()
        }
    }

    /**
     * Ad blocking needs the geosite database in the app's files dir — the same
     * place Xray's asset loader looks. Download it once on enable; on failure
     * the toggle reverts so settings never lie about the effective state.
     */
    private fun toggleBlockAds(on: Boolean) {
        if (!on) {
            blockAds.value = false
            SettingsStore.setBlockAds(this, false)
            return
        }
        if (blockAdsBusy.value) return
        blockAdsBusy.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val dst = File(filesDir, "geosite.dat")
            try {
                val bytes = URL(geositeUrl).openStream().use { input ->
                    input.readBytes()
                }
                if (bytes.isEmpty()) throw IllegalStateException("empty geosite.dat")
                val tmp = File(filesDir, "geosite.dat.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(dst)) {
                    dst.writeBytes(bytes)
                    tmp.delete()
                }
                withContext(Dispatchers.Main) {
                    blockAds.value = true
                    SettingsStore.setBlockAds(this@MainActivity, true)
                    Toast.makeText(this@MainActivity, R.string.block_ads_ready, Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                dst.delete()
                File(filesDir, "geosite.dat.tmp").delete()
                withContext(Dispatchers.Main) {
                    blockAds.value = false
                    SettingsStore.setBlockAds(this@MainActivity, false)
                    Toast.makeText(
                        this@MainActivity,
                        t.message ?: t.javaClass.simpleName,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                blockAdsBusy.value = false
            }
        }
    }

    private fun tcpPing(host: String, port: Int): Long = TcpPing.measure(host, port)

    private fun urlTestVisibleProfiles() {
        val list = ProfileStore.load(this)
        if (list.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            pingAllRunning.value = true
            pingResults.clear()
            try {
                val done = java.util.concurrent.atomic.AtomicInteger()
                coroutineScope {
                    list.forEach { p ->
                        launch {
                            val ms = tcpPing(p.address, p.port)
                            pingResults[p.id] = ms
                            pingAllProgress.value = done.incrementAndGet() to list.size
                        }
                    }
                }
                val live = list.filter { (pingResults[it.id] ?: -1L) >= 0 }
                val best = live.minByOrNull { pingResults[it.id] ?: Long.MAX_VALUE }
                withContext(Dispatchers.Main) {
                    if (best != null) VpnManager.connect(this@MainActivity, best)
                }
            } finally {
                pingAllRunning.value = false
                pingAllProgress.value = 0 to 0
            }
        }
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
        if (enabled) {
            refreshSubscription(sub.id)
        } else {
            // Disabled subscription: hide its profiles until re-enabled.
            // Stop the tunnel first if it runs on one of them.
            val active = VpnManager.current().profile
            if (active?.subscriptionId == sub.id &&
                VpnManager.current().state != VpnManager.State.OFF
            ) {
                VpnManager.stop(this)
            }
            ProfileStore.removeBySubscription(this, sub.id)
            refreshProfiles()
        }
        refreshSubscriptions()
    }

    private fun removeSubscription(sub: Subscription) {
        SubscriptionsStore.remove(this, sub.id)
        ProfileStore.removeBySubscription(this, sub.id)
        SubscriptionSyncScheduler.reschedule(this)
        refreshProfiles()
        refreshSubscriptions()
        Toast.makeText(this, R.string.subscription_removed, Toast.LENGTH_SHORT).show()
    }

    /** Decrypts a vault envelope off the UI thread and imports the payload. */
    private fun decryptAndImport(envelope: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val plain = try {
                ProfileVault.decrypt(envelope, password.toCharArray())
            } catch (_: ProfileVault.VaultException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.vault_wrong_password, Toast.LENGTH_LONG)
                        .show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) { importText(plain) }
        }
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
        // Encrypted vault: ask for the password first, then re-import the
        // decrypted payload.
        if (ProfileVault.isVault(text)) {
            vaultPendingText.value = text
            return
        }
        // Full crystaln/2 backup (settings + subscriptions + profiles) first;
        // anything else falls through to the profile-only parser.
        Backup.importFull(this, text)?.let { summary ->
            completeOnboarding()
            refreshProfiles()
            refreshSubscriptions()
            Toast.makeText(this, summary, Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = ProfileImporter.parseText(text)
        if (parsed.isEmpty()) {
            Toast.makeText(this, R.string.import_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        // Re-imports update the same server instead of stacking duplicates.
        completeOnboarding()
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

/**
 * Asks how to export: raw JSON (full fidelity, no protection) or an
 * AES-GCM "vault" encrypted with a user password. null password = raw.
 */
@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
) {
    var usePassword by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passwordOk = usePassword &&
        password.length >= 4 &&
        password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_action)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = usePassword,
                        onCheckedChange = { usePassword = it },
                    )
                    Text(stringResource(R.string.vault_protect))
                }
                if (usePassword) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.vault_password)) },
                        isError = password.isNotEmpty() && password.length < 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        label = { Text(stringResource(R.string.vault_confirm)) },
                        isError = confirm.isNotEmpty() && password != confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !usePassword || passwordOk,
                onClick = { onExport(if (usePassword) password else null) },
            ) {
                Text(stringResource(R.string.export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Password prompt for importing a vault envelope. */
@Composable
private fun VaultPasswordDialog(
    onDismiss: () -> Unit,
    onUnlock: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.vault_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.vault_password)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onUnlock(password) },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** "New release available" → opens the GitHub releases page. */
@Composable
private fun UpdateDialog(
    release: com.crystaln.vpn.data.UpdateChecker.Release?,
    currentVersion: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (release == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = { Text(stringResource(R.string.update_body, currentVersion, release.version)) },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.update_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
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

/**
 * Full-screen lock overlay shown while the biometric lock is armed. The
 * system prompt auto-fires once; the button re-shows it after a cancel.
 */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.lock_locked),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.lock_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            TextButton(onClick = onUnlock) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
