package com.crystaln.vpn.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.Backup
import com.crystaln.vpn.data.ConnectionDiagnostics
import com.crystaln.vpn.data.UpdateChecker
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileImporter
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.ProfileVault
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.TrafficStats
import com.crystaln.vpn.data.TrafficStatsStore
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionRefresher
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.desktop.Autostart
import com.crystaln.vpn.desktop.net.Binaries
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.screens.DiagnosticsScreen
import com.crystaln.vpn.desktop.ui.screens.OnboardingScreen
import com.crystaln.vpn.desktop.ui.screens.EditorScreen
import com.crystaln.vpn.desktop.ui.screens.MainScreen
import com.crystaln.vpn.desktop.ui.screens.SettingsScreen
import com.crystaln.vpn.desktop.ui.screens.ToolsScreen
import com.crystaln.vpn.desktop.ui.dialogs.ExportDialog
import com.crystaln.vpn.desktop.ui.dialogs.ImportDialog
import com.crystaln.vpn.desktop.ui.dialogs.PingToolDialog
import com.crystaln.vpn.desktop.ui.dialogs.UpdateDialog
import com.crystaln.vpn.desktop.ui.dialogs.UrlImportDialog
import com.crystaln.vpn.desktop.ui.dialogs.VaultPasswordDialog
import com.crystaln.vpn.desktop.ui.theme.CrystalNTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root composable: theme + navigation + all the wiring (dialogs, ping,
 * subscriptions, backup). Desktop port of Android's MainActivity — same
 * navigation order, same behaviors.
 */
@Composable
fun CrystalnApp() {
    val cfg = remember { AppDirs.configDir() }
    val scope = rememberCoroutineScope()

    // ── theme ────────────────────────────────────────────────────────────
    var themeMode by remember { mutableStateOf(SettingsStore.themeMode(cfg)) }
    var paletteId by remember { mutableStateOf(SettingsStore.paletteId(cfg)) }
    val dark = when (themeMode) {
        SettingsStore.ThemeMode.SYSTEM -> remember { systemDark() }
        SettingsStore.ThemeMode.LIGHT -> false
        SettingsStore.ThemeMode.DARK -> true
    }

    // ── settings state ──────────────────────────────────────────────────
    var autoConnect by remember { mutableStateOf(SettingsStore.autoConnect(cfg)) }
    var autoFastest by remember { mutableStateOf(SettingsStore.autoFastest(cfg)) }
    var startWithSystem by remember { mutableStateOf(Autostart.isEnabled()) }
    var dnsViaTunnel by remember { mutableStateOf(SettingsStore.dnsViaTunnel(cfg)) }
    var dnsServer by remember { mutableStateOf(SettingsStore.dnsServer(cfg)) }
    var blockAds by remember { mutableStateOf(SettingsStore.blockAds(cfg)) }
    var blockAdsBusy by remember { mutableStateOf(false) }
    var killSwitch by remember { mutableStateOf(SettingsStore.killSwitch(cfg)) }
    var anonymizeNames by remember { mutableStateOf(SettingsStore.anonymizeNames(cfg)) }
    var checkUrl by remember { mutableStateOf(SettingsStore.checkUrl(cfg)) }
    var checkTimeoutSec by remember { mutableStateOf(SettingsStore.checkTimeoutSec(cfg)) }
    var autoReconnect by remember { mutableStateOf(SettingsStore.autoReconnect(cfg)) }
    var fallbackProfileId by remember { mutableStateOf(SettingsStore.fallbackProfileId(cfg)) }
    var trafficLimitMb by remember { mutableStateOf(SettingsStore.trafficLimitMb(cfg)) }
    var splitTunnelMode by remember { mutableStateOf(SettingsStore.splitTunnelMode(cfg)) }
    var splitTunnelRules by remember { mutableStateOf(SettingsStore.splitTunnelRules(cfg)) }
    var urlTestBusy by remember { mutableStateOf(false) }
    var pingDialog by remember { mutableStateOf(false) }
    var tunnelMode by remember { mutableStateOf(SettingsStore.tunnelMode(cfg)) }
    var socksPort by remember { mutableStateOf(SettingsStore.socksPort(cfg).toString()) }
    var httpPort by remember { mutableStateOf(SettingsStore.httpPort(cfg).toString()) }
    var binariesBusy by remember { mutableStateOf(false) }
    var binariesStatus by remember { mutableStateOf(binStatus()) }

    // ── profiles ────────────────────────────────────────────────────────
    var profiles by remember { mutableStateOf(ProfileStore.load(cfg).toList()) }
    var selectedId by remember { mutableStateOf(ProfileStore.selectedId) }
    var profileStats by remember { mutableStateOf<Map<String, TrafficStats>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var shareProfile by remember { mutableStateOf<Profile?>(null) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    // ── subscriptions ───────────────────────────────────────────────────
    var subscriptions by remember { mutableStateOf(SubscriptionsStore.load(cfg)) }
    var subscriptionsBusy by remember { mutableStateOf(false) }
    var subscriptionBusyId by remember { mutableStateOf<String?>(null) }

    // ── ping ────────────────────────────────────────────────────────────
    val pingResults = remember { mutableStateMapOf<String, Long>() }
    var pingAllRunning by remember { mutableStateOf(false) }
    val pinging = remember {
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    }

    // ── diagnostics ─────────────────────────────────────────────────────
    var diagResults by remember { mutableStateOf<List<ConnectionDiagnostics.Result>?>(null) }
    var diagRunning by remember { mutableStateOf(false) }

    // ── first-run wizard + update check ──────────────────────────────
    var onboardingStep by remember { mutableStateOf(0) }
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var updatesBusy by remember { mutableStateOf(false) }

    // ── navigation + dialogs ────────────────────────────────────────────
    var nav by remember { mutableStateOf(if (SettingsStore.onboarded(cfg)) Nav.MAIN else Nav.ONBOARDING) }
    var importMenu by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var exportDialogVisible by remember { mutableStateOf(false) }
    var vaultPendingText by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }

    fun refreshProfiles() {
        profiles = ProfileStore.load(cfg).toList()
        selectedId = ProfileStore.selectedId
        profileStats = TrafficStatsStore.all(cfg)
    }

    fun refreshSubscriptions() {
        subscriptions = SubscriptionsStore.load(cfg)
    }

    // Ручная проверка обновлений (Настройки → О приложении). Найдена новая
    // версия — открывается тот же диалог; не найдена/ошибка сети — notice.
    fun checkUpdates() {
        if (updatesBusy) return
        updatesBusy = true
        scope.launch(Dispatchers.IO) {
            val release = UpdateChecker.check(APP_VERSION)
            withContext(Dispatchers.Main) {
                updatesBusy = false
                if (release != null) {
                    updateRelease = release
                } else {
                    notice = "Обновлений нет (или GitHub недоступен)"
                }
            }
        }
    }

    /** Ends the first-run wizard: any successful import or "skip" counts. */
    fun completeOnboarding() {
        if (!SettingsStore.onboarded(cfg)) {
            SettingsStore.setOnboarded(cfg, true)
            if (nav == Nav.ONBOARDING) nav = Nav.MAIN
        }
    }

    /** The profile the diagnostics screen is aimed at. */
    fun diagProfile(): Profile? = ProfileStore.selected(cfg) ?: ProfileStore.load(cfg).firstOrNull()

    val conn by TunnelManager.ui.collectAsState()

    fun onConnect() {
        when (conn.state) {
            TunnelManager.State.OFF -> {
                notice = ""
                val p = ProfileStore.selected(cfg)
                if (p != null) TunnelManager.connect(p) else notice = "Нет профиля"
            }
            else -> TunnelManager.stop()
        }
    }

    fun runDiagnostics() {
        if (diagRunning) return
        val profile = diagProfile() ?: run {
            notice = "Нет профиля"
            return
        }
        scope.launch(Dispatchers.IO) {
            diagRunning = true
            try {
                val results = ConnectionDiagnostics.run(profile)
                diagResults = results
            } finally {
                diagRunning = false
            }
        }
    }

    /** Measures the profile's server reachability on demand (TCP connect). */
    fun pingProfile(profile: Profile) {
        if (!pinging.add(profile.id)) return
        scope.launch(Dispatchers.IO) {
            val ms = tcpPing(profile.address, profile.port)
            pingResults[profile.id] = ms
            pinging.remove(profile.id)
        }
    }

    /**
     * Pings every profile in parallel (a few dozen sockets are cheap) and,
     * when auto-select is enabled while the tunnel is down, moves the
     * selection to the fastest reachable server.
     */
    fun pingAll() {
        if (pingAllRunning) return
        val list = profiles
        if (list.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            pingAllRunning = true
            try {
                coroutineScope {
                    list.forEach { p ->
                        launch { pingResults[p.id] = tcpPing(p.address, p.port) }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (autoFastest && TunnelManager.ui.value.state == TunnelManager.State.OFF) {
                        val best = profiles
                            .filter { (pingResults[it.id] ?: -1L) >= 0 }
                            .minByOrNull { pingResults[it.id] ?: Long.MAX_VALUE }
                        if (best != null && best.id != selectedId) {
                            ProfileStore.select(cfg, best.id)
                            refreshProfiles()
                        }
                    }
                }
            } finally {
                pingAllRunning = false
            }
        }
    }

    /** URL-test, Android's urlTestVisibleProfiles: ping everything, connect
     * the fastest. Only meaningful while the tunnel is down. */
    fun urlTest() {
        if (urlTestBusy) return
        if (TunnelManager.ui.value.state != TunnelManager.State.OFF) {
            notice = "Сначала отключите текущее соединение"
            return
        }
        val list = profiles
        if (list.isEmpty()) return
        urlTestBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    list.forEach { p -> launch { pingResults[p.id] = tcpPing(p.address, p.port) } }
                }
                val best = list
                    .filter { (pingResults[it.id] ?: -1L) >= 0 }
                    .minByOrNull { pingResults[it.id] ?: Long.MAX_VALUE }
                withContext(Dispatchers.Main) {
                    if (best != null) {
                        ProfileStore.select(cfg, best.id)
                        refreshProfiles()
                        // The notice must not leak the real name while the
                        // anonymization flag is on.
                        val label = if (anonymizeNames) {
                            com.crystaln.vpn.ui.ServerAlias.label(best, profiles.indexOf(best).coerceAtLeast(0))
                        } else {
                            best.name
                        }
                        notice = "Быстрейший: $label (${pingResults[best.id]} мс)"
                        TunnelManager.connect(best)
                    } else {
                        notice = "URL-тест: ни один сервер не ответил"
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { urlTestBusy = false }
            }
        }
    }

    /** crystaln/3 — settings without profiles/subscriptions, for a fresh PC. */
    fun exportSettingsOnly() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Экспорт только настроек"
            selectedFile = File("crystaln_settings.json")
        }
        Thread {
            val choice = chooser.showSaveDialog(null)
            if (choice != javax.swing.JFileChooser.APPROVE_OPTION) return@Thread
            val file = chooser.selectedFile ?: return@Thread
            try {
                file.writeText(Backup.exportSettingsOnly(cfg))
                notice = "Настройки экспортированы"
            } catch (t: Throwable) {
                notice = t.message ?: t.javaClass.simpleName
            }
        }.apply { isDaemon = true; start() }
    }

    /** Applies a crystaln/3 payload and re-reads every mirrored setting. */
    fun applySettingsOnlyImport(text: String) {
        val summary = Backup.importSettingsOnly(cfg, text)
        if (summary == null) {
            notice = "Это не crystaln/3 экспорт настроек"
            return
        }
        autoConnect = SettingsStore.autoConnect(cfg)
        autoFastest = SettingsStore.autoFastest(cfg)
        dnsViaTunnel = SettingsStore.dnsViaTunnel(cfg)
        dnsServer = SettingsStore.dnsServer(cfg)
        blockAds = SettingsStore.blockAds(cfg)
        killSwitch = SettingsStore.killSwitch(cfg)
        anonymizeNames = SettingsStore.anonymizeNames(cfg)
        checkUrl = SettingsStore.checkUrl(cfg)
        checkTimeoutSec = SettingsStore.checkTimeoutSec(cfg)
        autoReconnect = SettingsStore.autoReconnect(cfg)
        fallbackProfileId = SettingsStore.fallbackProfileId(cfg)
        trafficLimitMb = SettingsStore.trafficLimitMb(cfg)
        splitTunnelMode = SettingsStore.splitTunnelMode(cfg)
        splitTunnelRules = SettingsStore.splitTunnelRules(cfg)
        tunnelMode = SettingsStore.tunnelMode(cfg)
        socksPort = SettingsStore.socksPort(cfg).toString()
        httpPort = SettingsStore.httpPort(cfg).toString()
        themeMode = SettingsStore.themeMode(cfg)
        paletteId = SettingsStore.paletteId(cfg)
        notice = summary
    }

    fun importSettingsOnly() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Импорт настроек (профили не трогаем)"
            isAcceptAllFileFilterUsed = true
        }
        Thread {
            val choice = chooser.showOpenDialog(null)
            if (choice != javax.swing.JFileChooser.APPROVE_OPTION) return@Thread
            val file = chooser.selectedFile ?: return@Thread
            try {
                val bytes = file.inputStream().use { it.readNBytes(MAX_IMPORT_BYTES + 1) }
                if (bytes.size > MAX_IMPORT_BYTES) {
                    notice = "Файл слишком велик для конфига"
                    return@Thread
                }
                applySettingsOnlyImport(String(bytes, Charsets.UTF_8))
            } catch (t: Throwable) {
                notice = t.message ?: t.javaClass.simpleName
            }
        }.apply { isDaemon = true; start() }
    }

    fun refreshBinaries() {
        if (binariesBusy) return
        binariesBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                Binaries.downloadAll(needGeosite = blockAds) { step ->
                    notice = step
                }
                withContext(Dispatchers.Main) {
                    binariesStatus = binStatus()
                    notice = "Бинарники готовы: $binariesStatus"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    notice = "Ошибка загрузки: ${t.message ?: t.javaClass.simpleName}"
                }
            }
            binariesBusy = false
        }
    }

    fun importText(text: String) {
        if (text.length > MAX_IMPORT_BYTES) {
            notice = "Файл слишком велик для конфига"
            return
        }
        // Encrypted vault: ask for the password first, then re-import the
        // decrypted payload.
        if (ProfileVault.isVault(text)) {
            vaultPendingText = text
            return
        }
        // Settings-only crystaln/3 export: restore preferences without
        // touching the server list.
        if (text.trimStart().startsWith("{\"format\":\"crystaln/3\"") ||
            text.contains("\"format\": \"crystaln/3\"")
        ) {
            applySettingsOnlyImport(text)
            return
        }
        // Full crystaln/2 backup (settings + subscriptions + profiles) first;
        // anything else falls through to the profile-only parser.
        Backup.importFull(cfg, text)?.let { summary ->
            completeOnboarding()
            refreshProfiles()
            refreshSubscriptions()
            notice = summary
            return
        }
        val parsed = ProfileImporter.parseText(text)
        if (parsed.isEmpty()) {
            notice = "Нечего импортировать"
            return
        }
        // Re-imports update the same server instead of stacking duplicates.
        completeOnboarding()
        val merged = ProfileStore.mergeProfiles(cfg, parsed, subscriptionId = null, selectFirst = true)
        refreshProfiles()
        notice = "Импортировано: ${merged.added + merged.updated} · Выбрано: ${merged.selectedProfile?.name ?: ""}"
    }

    fun importClipboard() {
        val text = try {
            val cm = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val content = cm.getContents(null) as? java.awt.datatransfer.Transferable
            content?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
        } catch (_: Throwable) {
            null
        } ?: return
        importText(text)
    }

    /** Native file chooser → parse & import (with the size limit). */
    fun importFile() {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Установить конфиг из файла"
            isAcceptAllFileFilterUsed = true
        }
        Thread {
            val choice = chooser.showOpenDialog(null)
            if (choice != javax.swing.JFileChooser.APPROVE_OPTION) return@Thread
            val file = chooser.selectedFile ?: return@Thread
            try {
                val bytes = file.inputStream().use { it.readNBytes(MAX_IMPORT_BYTES + 1) }
                if (bytes.size > MAX_IMPORT_BYTES) {
                    notice = "Файл слишком велик для конфига"
                    return@Thread
                }
                importText(String(bytes, Charsets.UTF_8))
            } catch (t: Throwable) {
                notice = t.message ?: t.javaClass.simpleName
            }
        }.apply { isDaemon = true; start() }
    }

    fun exportToFile(password: String?) {
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Экспорт профилей в файл"
            selectedFile = File(if (password == null) "crystaln_profiles.json" else "crystaln_vault.json")
        }
        Thread {
            val choice = chooser.showSaveDialog(null)
            if (choice != javax.swing.JFileChooser.APPROVE_OPTION) return@Thread
            val file = chooser.selectedFile ?: return@Thread
            try {
                var json = Backup.exportFull(cfg)
                // Password set by ExportDialog → write a vault envelope instead
                // of raw credentials.
                password?.let { pw -> json = ProfileVault.encrypt(json, pw.toCharArray()) }
                file.writeText(json)
                notice = "Профили экспортированы"
            } catch (t: Throwable) {
                notice = t.message ?: t.javaClass.simpleName
            }
        }.apply { isDaemon = true; start() }
    }

    /** Decrypts a vault envelope off the UI thread and imports the payload. */
    fun decryptAndImport(envelope: String, password: String) {
        scope.launch(Dispatchers.IO) {
            val plain = try {
                ProfileVault.decrypt(envelope, password.toCharArray())
            } catch (_: ProfileVault.VaultException) {
                withContext(Dispatchers.Main) { notice = "Неверный пароль или файл повреждён" }
                return@launch
            }
            withContext(Dispatchers.Main) { importText(plain) }
        }
    }

    /**
     * Ad blocking needs the geosite database next to the binaries — the same
     * place Xray's asset loader looks. Download it once on enable; on failure
     * the toggle reverts so settings never lie about the effective state.
     */
    fun toggleBlockAds(on: Boolean) {
        if (!on) {
            blockAds = false
            SettingsStore.setBlockAds(cfg, false)
            return
        }
        if (blockAdsBusy) return
        blockAdsBusy = true
        scope.launch(Dispatchers.IO) {
            try {
                Binaries.downloadAll(needGeosite = true) { step ->
                    notice = step
                }
                withContext(Dispatchers.Main) {
                    blockAds = true
                    SettingsStore.setBlockAds(cfg, true)
                    notice = "База рекламных доменов загружена"
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    blockAds = false
                    SettingsStore.setBlockAds(cfg, false)
                    notice = "Ошибка загрузки: ${t.message ?: t.javaClass.simpleName}"
                }
            } finally {
                withContext(Dispatchers.Main) { blockAdsBusy = false }
            }
        }
    }

    /** Adds and immediately refreshes a saved subscription. */
    fun addSubscription(rawUrl: String) {
        val url = SubscriptionsStore.normalizeUrl(rawUrl)
        if (url == null) {
            notice = "Некорректная ссылка подписки"
            return
        }
        if (subscriptionsBusy) return
        scope.launch(Dispatchers.IO) {
            subscriptionsBusy = true
            try {
                val sub = SubscriptionsStore.add(cfg, url)
                subscriptionBusyId = sub.id
                val result = SubscriptionRefresher.refresh(cfg, sub)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    notice = if (result.ok) {
                        "Подписка добавлена: серверов ${result.added + result.updated}"
                    } else {
                        "Подписка сохранена, но серверов не найдено"
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    subscriptionBusyId = null
                    subscriptionsBusy = false
                }
            }
        }
    }

    fun refreshSubscription(id: String) {
        if (subscriptionsBusy) return
        val sub = SubscriptionsStore.byId(cfg, id) ?: return
        scope.launch(Dispatchers.IO) {
            subscriptionsBusy = true
            subscriptionBusyId = id
            try {
                val result = SubscriptionRefresher.refresh(cfg, sub)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    notice = if (result.ok) {
                        "Подписка обновлена: +${result.added} / ~${result.updated} / −${result.removed}"
                    } else {
                        "Не удалось обновить подписку"
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    subscriptionBusyId = null
                    subscriptionsBusy = false
                }
            }
        }
    }

    fun refreshAllSubscriptions() {
        if (subscriptionsBusy) return
        scope.launch(Dispatchers.IO) {
            subscriptionsBusy = true
            try {
                val results = SubscriptionRefresher.refreshAll(cfg)
                withContext(Dispatchers.Main) {
                    refreshProfiles()
                    refreshSubscriptions()
                    val failed = results.count { !it.second.ok }
                    notice = if (failed == 0) "Подписки обновлены" else "Не удалось обновить подписку"
                }
            } finally {
                withContext(Dispatchers.Main) { subscriptionsBusy = false }
            }
        }
    }

    // ── launch-time effects ─────────────────────────────────────────────

    // Само-восстановление автозагрузки: настройка включена, а запись ОС
    // отсутствует (переустановка приложения, смена пути) — восстанавливаем.
    LaunchedEffect(Unit) {
        if (SettingsStore.startWithSystem(cfg) && !Autostart.isEnabled()) {
            scope.launch(Dispatchers.IO) { Autostart.sync(true) }
        }
    }

    // Auto-connect the selected profile on cold launch, if enabled.
    LaunchedEffect(Unit) {
        if (SettingsStore.autoConnect(cfg) &&
            TunnelManager.ui.value.state == TunnelManager.State.OFF
        ) {
            if (SettingsStore.autoFastest(cfg)) {
                pingAll()
                while (pingAllRunning) delay(100)
            }
            val p = ProfileStore.selected(cfg) ?: ProfileStore.load(cfg).firstOrNull()
            if (p != null) {
                ProfileStore.select(cfg, p.id)
                refreshProfiles()
                TunnelManager.connect(p)
            }
        }
    }

    // Live per-profile usage totals refresh every ~10 s while the VPN runs.
    LaunchedEffect(Unit) {
        var ticks = 0
        TunnelManager.ui.collect {
            if (++ticks % 10 == 0 && TunnelManager.ui.value.state == TunnelManager.State.CONNECTED) {
                profileStats = TrafficStatsStore.all(cfg)
            }
        }
    }

    // One-time update check against GitHub releases (best-effort, silent on
    // any network error). APP_VERSION must match packageVersion in
    // desktop/build.gradle.kts.
    LaunchedEffect(Unit) {
        val release = scope.async(Dispatchers.IO) { UpdateChecker.check(APP_VERSION) }.await()
        updateRelease = release
    }

    // Background subscription refresh every 6 hours (Android: WorkManager).
    LaunchedEffect(Unit) {
        while (true) {
            delay(6 * 60 * 60 * 1000L)
            scope.launch(Dispatchers.IO) {
                SubscriptionRefresher.refreshAll(cfg)
                withContext(Dispatchers.Main) { refreshSubscriptions() }
            }
        }
    }

    CrystalNTheme(darkTheme = dark, paletteId = paletteId) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            AnimatedContent(
                targetState = nav,
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
                    Nav.ONBOARDING -> OnboardingScreen(
                        isDark = dark,
                        step = onboardingStep,
                        onStep = { onboardingStep = it },
                        binariesReady = Binaries.status().awgReady,
                        binariesBusy = binariesBusy,
                        binariesStatus = binariesStatus,
                        onUpdateBinaries = { refreshBinaries() },
                        onImportClipboard = {
                            completeOnboarding()
                            importClipboard()
                        },
                        onImportFile = {
                            completeOnboarding()
                            importFile()
                        },
                        onImportUrl = {
                            completeOnboarding()
                            urlDialog = true
                        },
                        onFinish = { completeOnboarding() },
                    )

                    Nav.MAIN -> MainScreen(
                        isDark = dark,
                        state = conn,
                        profiles = profiles,
                        selectedId = selectedId,
                        anonymizeNames = anonymizeNames,
                        onSelect = { p ->
                            ProfileStore.select(cfg, p.id)
                            refreshProfiles()
                        },
                        onEdit = { p ->
                            editing = p
                            nav = Nav.EDITOR
                        },
                        onDelete = { p ->
                            // Stop the tunnel first if this profile is live.
                            scope.launch(Dispatchers.IO) {
                                if (TunnelManager.ui.value.profile?.id == p.id) TunnelManager.stop()
                                ProfileStore.remove(cfg, p.id)
                                withContext(Dispatchers.Main) { refreshProfiles() }
                            }
                        },
                        onAdd = {
                            editing = null
                            nav = Nav.EDITOR
                        },
                        onImport = { importMenu = true },
                        onConnect = { onConnect() },
                        onSettings = { nav = Nav.SETTINGS },
                        onTools = { nav = Nav.TOOLS },
                        pingResults = pingResults,
                        onPing = { p -> pingProfile(p) },
                        pingAllRunning = pingAllRunning,
                        onPingAll = { pingAll() },
                        trafficStats = profileStats,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        subscriptionTitles = subscriptions.associate { it.id to it.name },
                        onShare = { shareProfile = it },
                        shareProfile = shareProfile,
                        onDismissShare = { shareProfile = null },
                    )

                    Nav.SETTINGS -> SettingsScreen(
                        isDark = dark,
                        themeMode = themeMode,
                        onThemeChange = { mode ->
                            themeMode = mode
                            SettingsStore.setThemeMode(cfg, mode)
                        },
                        paletteId = paletteId,
                        onPaletteChange = { id ->
                            paletteId = id
                            SettingsStore.setPaletteId(cfg, id)
                        },
                        autoConnect = autoConnect,
                        onAutoConnectChange = { on ->
                            autoConnect = on
                            SettingsStore.setAutoConnect(cfg, on)
                        },
                        autoFastest = autoFastest,
                        onAutoFastestChange = { on ->
                            autoFastest = on
                            SettingsStore.setAutoFastest(cfg, on)
                        },
                        startWithSystem = startWithSystem,
                        onStartWithSystemChange = { on ->
                            if (Autostart.setEnabled(on)) {
                                startWithSystem = on
                                SettingsStore.setStartWithSystem(cfg, on)
                                notice = if (on) "Автозагрузка включена" else "Автозагрузка выключена"
                            } else {
                                notice = "Не удалось настроить автозагрузку (dev-запуск без установки?)"
                            }
                        },
                        dnsViaTunnel = dnsViaTunnel,
                        onDnsViaTunnelChange = { on ->
                            dnsViaTunnel = on
                            SettingsStore.setDnsViaTunnel(cfg, on)
                        },
                        dnsServer = dnsServer,
                        onDnsServerChange = { value ->
                            dnsServer = value
                            SettingsStore.setDnsServer(cfg, value)
                        },
                        blockAds = blockAds,
                        onBlockAdsChange = { on -> toggleBlockAds(on) },
                        blockAdsBusy = blockAdsBusy,
                        anonymizeNames = anonymizeNames,
                        onAnonymizeNamesChange = { on ->
                            anonymizeNames = on
                            SettingsStore.setAnonymizeNames(cfg, on)
                        },
                        checkUrl = checkUrl,
                        onCheckUrlChange = { v ->
                            checkUrl = v
                            SettingsStore.setCheckUrl(cfg, v)
                        },
                        checkTimeoutSec = checkTimeoutSec,
                        onCheckTimeoutSecChange = { v ->
                            checkTimeoutSec = v
                            SettingsStore.setCheckTimeoutSec(cfg, v)
                        },
                        autoReconnect = autoReconnect,
                        onAutoReconnectChange = { on ->
                            autoReconnect = on
                            SettingsStore.setAutoReconnect(cfg, on)
                        },
                        fallbackProfileId = fallbackProfileId,
                        onFallbackProfileIdChange = { id ->
                            fallbackProfileId = id
                            SettingsStore.setFallbackProfileId(cfg, id)
                        },
                        profiles = profiles,
                        trafficLimitMb = trafficLimitMb,
                        onTrafficLimitMbChange = { v ->
                            trafficLimitMb = v
                            SettingsStore.setTrafficLimitMb(cfg, v)
                        },
                        onPingTool = { pingDialog = true },
                        onExportSettings = { exportSettingsOnly() },
                        onImportSettings = { importSettingsOnly() },
                        tunnelMode = tunnelMode,
                        onTunnelModeChange = { m ->
                            tunnelMode = m
                            SettingsStore.setTunnelMode(cfg, m)
                        },
                        socksPort = socksPort,
                        onSocksPortChange = { v ->
                            socksPort = v
                            v.toIntOrNull()?.let { SettingsStore.setSocksPort(cfg, it) }
                        },
                        httpPort = httpPort,
                        onHttpPortChange = { v ->
                            httpPort = v
                            v.toIntOrNull()?.let { SettingsStore.setHttpPort(cfg, it) }
                        },
                        onRefreshBinaries = { refreshBinaries() },
                        binariesBusy = binariesBusy,
                        binariesStatus = binariesStatus,
                        subscriptions = subscriptions,
                        subscriptionsBusy = subscriptionsBusy,
                        subscriptionBusyId = subscriptionBusyId,
                        onAddSubscription = { url -> addSubscription(url) },
                        onRefreshSubscription = { sub -> refreshSubscription(sub.id) },
                        onRefreshAllSubscriptions = { refreshAllSubscriptions() },
                        onToggleSubscription = { sub, on ->
                            SubscriptionsStore.update(cfg, sub.copy(enabled = on))
                            refreshSubscriptions()
                            if (on) refreshSubscription(sub.id)
                        },
                        onRemoveSubscription = { sub ->
                            SubscriptionsStore.remove(cfg, sub.id)
                            ProfileStore.removeBySubscription(cfg, sub.id)
                            refreshProfiles()
                            refreshSubscriptions()
                            notice = "Подписка удалена"
                        },
                        onImport = {
                            importMenu = false
                            importFile()
                        },
                        onExport = {
                            importMenu = false
                            exportDialogVisible = true
                        },
                        onCheckUpdates = { checkUpdates() },
                        updatesBusy = updatesBusy,
                        updateAvailable = updateRelease?.version,
                        onTools = { nav = Nav.TOOLS },
                        onBack = { nav = Nav.MAIN },
                    )

                    Nav.TOOLS -> ToolsScreen(
                        isDark = dark,
                        killSwitch = killSwitch,
                        onKillSwitchChange = { on ->
                            killSwitch = on
                            SettingsStore.setKillSwitch(cfg, on)
                        },
                        splitTunnelMode = splitTunnelMode,
                        onSplitTunnelModeChange = { m ->
                            splitTunnelMode = m
                            SettingsStore.setSplitTunnelMode(cfg, m)
                        },
                        splitTunnelRules = splitTunnelRules,
                        onSplitTunnelRulesChange = { r ->
                            splitTunnelRules = r
                            SettingsStore.setSplitTunnelRules(cfg, r)
                        },
                        urlTestBusy = urlTestBusy,
                        onUrlTest = { urlTest() },
                        onPingTool = { pingDialog = true },
                        onDiagnostics = {
                            diagResults = null
                            nav = Nav.DIAGNOSTICS
                        },
                        onBack = { nav = Nav.MAIN },
                        onMain = { nav = Nav.MAIN },
                        onSettings = { nav = Nav.SETTINGS },
                    )

                    Nav.DIAGNOSTICS -> DiagnosticsScreen(
                        isDark = dark,
                        profileName = diagProfile()?.name ?: "",
                        protocolName = diagProfile()?.protocol?.displayName ?: "",
                        addressPort = diagProfile()?.let { "${it.address}:${it.port}" } ?: "",
                        vpnRunning = conn.state == TunnelManager.State.CONNECTED,
                        results = diagResults,
                        running = diagRunning,
                        onRun = { runDiagnostics() },
                        onBack = { nav = Nav.TOOLS },
                    )

                    Nav.EDITOR -> EditorScreen(
                        existing = editing,
                        isDark = dark,
                        onSave = { p ->
                            ProfileStore.upsert(cfg, p)
                            refreshProfiles()
                            nav = Nav.MAIN
                        },
                        onBack = { nav = Nav.MAIN },
                    )
                }
            }

            // Inline toast-like notice (desktop has no Toast).
            if (notice.isNotBlank()) {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 108.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // ── dialogs ─────────────────────────────────────────────────────
        if (importMenu) {
            ImportDialog(
                onDismiss = { importMenu = false },
                onClipboard = {
                    importMenu = false
                    importClipboard()
                },
                onFile = {
                    importMenu = false
                    importFile()
                },
                onExport = {
                    importMenu = false
                    exportDialogVisible = true
                },
                onUrl = {
                    importMenu = false
                    urlDialog = true
                },
            )
        }

        if (exportDialogVisible) {
            ExportDialog(
                onDismiss = { exportDialogVisible = false },
                onExport = { password ->
                    exportDialogVisible = false
                    exportToFile(password)
                },
            )
        }

        if (pingDialog) {
            PingToolDialog(onDismiss = { pingDialog = false })
        }

        if (vaultPendingText != null) {
            VaultPasswordDialog(
                onDismiss = { vaultPendingText = null },
                onUnlock = { password ->
                    val envelope = vaultPendingText
                    vaultPendingText = null
                    if (envelope != null) decryptAndImport(envelope, password)
                },
            )
        }

        if (urlDialog) {
            UrlImportDialog(
                onDismiss = { urlDialog = false },
                onOk = { url ->
                    urlDialog = false
                    scope.launch(Dispatchers.IO) {
                        val text = try {
                            downloadText(url)
                        } catch (_: Throwable) {
                            withContext(Dispatchers.Main) { notice = "Не удалось скачать конфиг" }
                            return@launch
                        }
                        withContext(Dispatchers.Main) { importText(text) }
                    }
                },
            )
        }

        if (updateRelease != null) {
            UpdateDialog(
                release = updateRelease,
                currentVersion = APP_VERSION,
                onOpen = {
                    val url = updateRelease?.url ?: UpdateChecker.RELEASES_URL
                    updateRelease = null
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop.getDesktop().browse(java.net.URI(url))
                        } else {
                            ProcessBuilder("xdg-open", url).start()
                        }
                    } catch (t: Throwable) {
                        notice = t.message ?: t.javaClass.simpleName
                    }
                },
                onDismiss = { updateRelease = null },
            )
        }
    }
}

/** Screens of the desktop app, in navigation order. */
internal enum class Nav { MAIN, SETTINGS, TOOLS, DIAGNOSTICS, EDITOR, ONBOARDING }
