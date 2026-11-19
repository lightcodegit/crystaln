package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.DailyTraffic
import com.crystaln.vpn.data.TrafficStatsStore
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.ui.ServerAlias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.resourcePainter
import com.crystaln.vpn.desktop.ui.glass.CircleGlassButton
import com.crystaln.vpn.desktop.ui.glass.Glass
import com.crystaln.vpn.desktop.ui.glass.GlassDivider
import com.crystaln.vpn.desktop.ui.glass.GlassSegmented
import com.crystaln.vpn.desktop.ui.glass.GlassTab
import com.crystaln.vpn.desktop.ui.glass.GlassTabBar
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.SectionLabel
import com.crystaln.vpn.desktop.ui.glass.liquidGlass
import com.crystaln.vpn.desktop.ui.theme.CrystalPalettes
import com.crystaln.vpn.desktop.ui.theme.LocalCrystalPalette

/** Desktop version string (matches the deb package version). */
private const val DESKTOP_VERSION = "1.0.0"

@Composable
fun SettingsScreen(
    isDark: Boolean,
    themeMode: SettingsStore.ThemeMode,
    onThemeChange: (SettingsStore.ThemeMode) -> Unit,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    autoFastest: Boolean,
    onAutoFastestChange: (Boolean) -> Unit,
    startWithSystem: Boolean,
    onStartWithSystemChange: (Boolean) -> Unit,
    dnsViaTunnel: Boolean,
    onDnsViaTunnelChange: (Boolean) -> Unit,
    dnsServer: String,
    onDnsServerChange: (String) -> Unit,
    blockAds: Boolean,
    onBlockAdsChange: (Boolean) -> Unit,
    blockAdsBusy: Boolean,
    anonymizeNames: Boolean,
    onAnonymizeNamesChange: (Boolean) -> Unit,
    checkUrl: String,
    onCheckUrlChange: (String) -> Unit,
    checkTimeoutSec: Int,
    onCheckTimeoutSecChange: (Int) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    fallbackProfileId: String?,
    onFallbackProfileIdChange: (String?) -> Unit,
    profiles: List<Profile>,
    trafficLimitMb: Int,
    onTrafficLimitMbChange: (Int) -> Unit,
    onPingTool: () -> Unit,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit,
    tunnelMode: SettingsStore.TunnelMode,
    onTunnelModeChange: (SettingsStore.TunnelMode) -> Unit,
    socksPort: String,
    onSocksPortChange: (String) -> Unit,
    httpPort: String,
    onHttpPortChange: (String) -> Unit,
    onRefreshBinaries: () -> Unit,
    binariesBusy: Boolean,
    binariesStatus: String,
    subscriptions: List<Subscription>,
    subscriptionsBusy: Boolean,
    subscriptionBusyId: String?,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onRefreshAllSubscriptions: () -> Unit,
    onToggleSubscription: (Subscription, Boolean) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCheckUpdates: () -> Unit,
    updatesBusy: Boolean,
    updateAvailable: String?,
    onBack: () -> Unit,
    onTools: () -> Unit = {},
) {
    val glassDark = isDark
    var addSubscriptionVisible by remember { mutableStateOf(false) }
    var trafficLimitText by remember(trafficLimitMb) { mutableStateOf(trafficLimitMb.toString()) }

    var daily by remember { mutableStateOf(emptyList<DailyTraffic>()) }
    LaunchedEffect(Unit) {
        while (true) {
            daily = withContext(Dispatchers.IO) { TrafficStatsStore.dailyHistory(AppDirs.configDir()) }
            delay(10_000)
        }
    }
    var jellyGame by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LiquidBlobs(isDark)

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                // Header: glass back button + big title.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleGlassButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        isDark = glassDark,
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Настройки",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Appearance ─────────────────────────────────────
                    SectionLabel("Внешний вид")
                    SettingsPanel(isDark = glassDark) {
                        Text(
                            "Тема",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassSegmented(
                            options = listOf("Системная", "Светлая", "Тёмная"),
                            selected = when (themeMode) {
                                SettingsStore.ThemeMode.SYSTEM -> 0
                                SettingsStore.ThemeMode.LIGHT -> 1
                                SettingsStore.ThemeMode.DARK -> 2
                            },
                            onSelect = { i ->
                                onThemeChange(
                                    when (i) {
                                        0 -> SettingsStore.ThemeMode.SYSTEM
                                        1 -> SettingsStore.ThemeMode.LIGHT
                                        else -> SettingsStore.ThemeMode.DARK
                                    },
                                )
                            },
                            isDark = glassDark,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        Text(
                            "Палитра",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        PalettePicker(
                            paletteId = paletteId,
                            onPaletteChange = onPaletteChange,
                        )
                    }

                    // ── Connection ─────────────────────────────────────
                    SectionLabel("Подключение")
                    SettingsPanel(isDark = glassDark) {
                        SwitchRow(
                            title = "Автоподключение",
                            subtitle = "Соединять выбранный профиль при запуске приложения",
                            checked = autoConnect,
                            onChange = onAutoConnectChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Автовыбор самого быстрого",
                            subtitle = "Перед подключением проверять серверы и брать самый отзывчивый живой",
                            checked = autoFastest,
                            onChange = onAutoFastestChange,
                        )
                        if (com.crystaln.vpn.desktop.Autostart.isSupported()) {
                            GlassDivider(Modifier.padding(vertical = 14.dp))
                            SwitchRow(
                                title = "Запускать при входе в систему",
                                subtitle = "Автозагрузка через реестр Windows (HKCU Run)",
                                checked = startWithSystem,
                                onChange = onStartWithSystemChange,
                            )
                        }
                    }

                    // ── Desktop engine (desktop-only: no such section on Android) ──
                    SectionLabel("Система")
                    SettingsPanel(isDark = glassDark) {
                        Text(
                            "Режим работы",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassSegmented(
                            options = listOf("TUN (root / admin)", "Прокси"),
                            selected = if (tunnelMode == SettingsStore.TunnelMode.TUN) 0 else 1,
                            onSelect = { i ->
                                onTunnelModeChange(
                                    if (i == 0) SettingsStore.TunnelMode.TUN else SettingsStore.TunnelMode.PROXY,
                                )
                            },
                            isDark = glassDark,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = socksPort,
                                onValueChange = { onSocksPortChange(it.filter { c -> c.isDigit() }.take(5)) },
                                singleLine = true,
                                label = { Text("SOCKS-порт") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = httpPort,
                                onValueChange = { onHttpPortChange(it.filter { c -> c.isDigit() }.take(5)) },
                                singleLine = true,
                                label = { Text("HTTP-порт") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ядра: $binariesStatus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            text = "Обновить бинарники",
                            icon = Icons.Default.Refresh,
                            accent = false,
                            isDark = glassDark,
                            enabled = !binariesBusy,
                            onClick = onRefreshBinaries,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ── Privacy ────────────────────────────────────────
                    SectionLabel("Приватность")
                    SettingsPanel(isDark = glassDark) {
                        SwitchRow(
                            title = "DNS через туннель",
                            subtitle = "Перехватывать DNS-запросы и отправлять их через VPN, чтобы провайдер не видел имена сайтов",
                            checked = dnsViaTunnel,
                            onChange = onDnsViaTunnelChange,
                        )
                        if (dnsViaTunnel) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = dnsServer,
                                onValueChange = onDnsServerChange,
                                singleLine = true,
                                label = { Text("DNS-сервер") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Блокировать рекламу",
                            subtitle = "Отбрасывать рекламные и трекерские домены (нужна база geosite, скачается один раз)",
                            checked = blockAds,
                            enabled = !blockAdsBusy,
                            onChange = onBlockAdsChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Обезличивать имена серверов",
                            subtitle = "Показывать флаг страны и номер вместо названий профилей: «🇩🇪 #4»",
                            checked = anonymizeNames,
                            onChange = onAnonymizeNamesChange,
                        )

                        }

                    // ── Reliability & tools ────────────────────────────────
                    SectionLabel("Надёжность и инструменты")
                    SettingsPanel(isDark = glassDark) {
                        OutlinedTextField(
                            value = checkUrl,
                            onValueChange = onCheckUrlChange,
                            singleLine = true,
                            label = { Text("Ссылка для проверки подключения") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = checkTimeoutSec.toString(),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { onCheckTimeoutSecChange(it) }
                            },
                            singleLine = true,
                            label = { Text("Таймаут проверки (сек)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Автопереподключение",
                            subtitle = "Переподключаться при падении туннеля (до 3 попыток), затем переходить на fallback-профиль",
                            checked = autoReconnect,
                            onChange = onAutoReconnectChange,
                        )
                        if (autoReconnect) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Fallback-профиль при падении туннеля:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            var fallbackExpanded by remember { mutableStateOf(false) }
                            Box {
                                PillButton(
                                    text = fallbackProfileId?.let { id ->
                                        profiles.firstOrNull { it.id == id }?.let { p ->
                                            if (anonymizeNames) ServerAlias.label(p, profiles.indexOf(p)) else p.name
                                        } ?: "следующий в списке"
                                    } ?: "следующий в списке",
                                    icon = Icons.Default.ArrowDropDown,
                                    accent = false,
                                    isDark = glassDark,
                                    onClick = { fallbackExpanded = true },
                                )
                                DropdownMenu(
                                    expanded = fallbackExpanded,
                                    onDismissRequest = { fallbackExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Следующий в списке") },
                                        onClick = {
                                            onFallbackProfileIdChange(null)
                                            fallbackExpanded = false
                                        },
                                    )
                                    profiles.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(if (anonymizeNames) ServerAlias.label(p, profiles.indexOf(p)) else p.name) },
                                            onClick = {
                                                onFallbackProfileIdChange(p.id)
                                                fallbackExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassDivider(Modifier.padding(vertical = 6.dp))
                        // ── Ping tool for any address ──
                        PillButton(
                            text = "Ping-инструмент",
                            icon = Icons.Default.NetworkCheck,
                            accent = false,
                            isDark = glassDark,
                            onClick = onPingTool,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassDivider(Modifier.padding(vertical = 6.dp))
                        // ── Monthly traffic limit ──
                        Text(
                            "Лимит трафика в месяц (МБ, 0 = без лимита)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = trafficLimitText,
                            onValueChange = { v ->
                                trafficLimitText = v
                                v.toIntOrNull()?.let(onTrafficLimitMbChange)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }


                    // ── Traffic history ──────────────────────────────────
                    SectionLabel("История трафика")
                    SettingsPanel(isDark = glassDark) {
                        DailyHistoryChart(daily)
                    }

                    // ── Subscriptions ───────────────────────────────────
                    SectionLabel("Подписки")
                    SettingsPanel(isDark = glassDark) {
                        if (subscriptions.isEmpty()) {
                            Text(
                                "Нет подписок. Добавьте ссылку, чтобы серверы обновлялись автоматически.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        subscriptions.forEach { sub ->
                            GlassDivider(Modifier.padding(vertical = 12.dp))
                            SwitchRow(
                                title = sub.name,
                                subtitle = subscriptionStatusText(sub),
                                checked = sub.enabled,
                                enabled = !subscriptionsBusy,
                                onChange = { on -> onToggleSubscription(sub, on) },
                            )
                            Text(
                                sub.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            sub.lastError?.takeIf { !sub.enabled || !sub.autoRefresh || it.isNotBlank() }
                                ?.let { error ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton(
                                    text = "Обновить",
                                    icon = Icons.Default.Refresh,
                                    accent = true,
                                    isDark = glassDark,
                                    enabled = !subscriptionsBusy &&
                                        (subscriptionBusyId == null || subscriptionBusyId != sub.id),
                                    onClick = { onRefreshSubscription(sub) },
                                    modifier = Modifier.weight(1f),
                                )
                                PillButton(
                                    text = "Удалить",
                                    icon = null,
                                    accent = false,
                                    isDark = glassDark,
                                    enabled = !subscriptionsBusy,
                                    onClick = { onRemoveSubscription(sub) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (subscriptions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Фоновое обновление: раз в 6 часов.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = "Добавить подписку",
                                icon = Icons.Default.Add,
                                accent = true,
                                isDark = glassDark,
                                enabled = !subscriptionsBusy,
                                onClick = { addSubscriptionVisible = true },
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = "Обновить все",
                                icon = Icons.Default.Refresh,
                                accent = false,
                                isDark = glassDark,
                                enabled = !subscriptionsBusy && subscriptions.any { it.enabled },
                                onClick = onRefreshAllSubscriptions,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── Data ───────────────────────────────────────────
                    SectionLabel("Профили и данные")
                    SettingsPanel(isDark = glassDark) {
                        Text(
                            "Импорт: файл конфигурации (.conf / .txt / .json), ссылки vless:// trojan:// ss:// vmess:// socks5:// или wg-quick конфиг.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = "Импорт",
                                icon = Icons.Default.FileOpen,
                                accent = true,
                                isDark = glassDark,
                                onClick = onImport,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = "Экспорт",
                                icon = Icons.Default.FileDownload,
                                accent = false,
                                isDark = glassDark,
                                onClick = onExport,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = "Только настройки",
                                icon = Icons.Default.FileUpload,
                                accent = false,
                                isDark = glassDark,
                                onClick = onExportSettings,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = "Настройки из файла",
                                icon = Icons.Default.FileOpen,
                                accent = false,
                                isDark = glassDark,
                                onClick = onImportSettings,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── About ──────────────────────────────────────────
                    SectionLabel("О приложении")
                    SettingsPanel(isDark = glassDark) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CrystalN",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { jellyGame = true },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                DESKTOP_VERSION,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ядра: Xray (VMess/VLESS/Trojan/Shadowsocks/SOCKS/HTTP), WireGuard и AmneziaWG (amneziawg-go, junk-параметры Jc/Jmin/Jmax/S1/S2/H1–H4).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        updateAvailable?.let { version ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Доступна новая версия: $version",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        PillButton(
                            text = "Проверить обновления",
                            icon = Icons.Default.Refresh,
                            accent = false,
                            isDark = glassDark,
                            enabled = !updatesBusy,
                            onClick = onCheckUpdates,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Hidden-ish easter egg: the jelly logo mini game.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircleGlassButton(
                            icon = Icons.Default.SportsEsports,
                            contentDescription = "CrystalN jelly",
                            isDark = glassDark,
                            size = 40.dp,
                            onClick = { jellyGame = true },
                        )
                    }

                    // Clearance so the last rows sit above the tab bar.
                    Spacer(Modifier.height(170.dp))
                }
            }
        }

        if (jellyGame) JellyGameOverlay(isDark = isDark, onDismiss = { jellyGame = false })

    if (addSubscriptionVisible) {
            AddSubscriptionDialog(
                initialUrl = "",
                busy = subscriptionsBusy,
                onDismiss = { addSubscriptionVisible = false },
                onAdd = { url ->
                    addSubscriptionVisible = false
                    onAddSubscription(url)
                },
            )
        }

        // Floating liquid-glass tab bar, iOS-style.
        GlassTabBar(
            isDark = glassDark,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, "Подключение"),
                GlassTab(Icons.Default.Build, "Инструменты"),
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 2,
            onSelect = { index ->
                when (index) {
                    0 -> onBack()
                    1 -> onTools()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

/** Compact 14-day bar chart of daily traffic (newest on the right). */
@Composable
internal fun DailyHistoryChart(daily: List<DailyTraffic>) {
    val days = daily.take(14).sortedBy { it.date }
    if (days.isEmpty()) {
        Text(
            "Пока нет данных: история появится после первой сессии.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val maxV = days.maxOf { it.rx + it.tx }.coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { d ->
            val v = d.rx + d.tx
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    d.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height((78f * v / maxV).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Всего за период: ${formatBytes(days.sumOf { it.rx + it.tx })} · лучший день: ${formatBytes(maxV)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Hidden mini game, desktop port of the Android JellyGameOverlay: a jelly
 * logo you can flick around — it glides with inertia, bounces off the
 * walls and wobbles. Physics integrates per frame; transforms are read
 * inside graphicsLayer so animation does not recompose the tree.
 */
@Composable
fun JellyGameOverlay(isDark: Boolean, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
        ) {
            val jellySize = 96.dp
            val maxX = with(LocalDensity.current) { (maxWidth - jellySize).toPx() }
            val maxY = with(LocalDensity.current) { (maxHeight - jellySize).toPx() }

            var pos by remember { mutableStateOf(Offset(maxX / 2f, maxY / 3f)) }
            var vel by remember { mutableStateOf(Offset.Zero) }
            var dragging by remember { mutableStateOf(false) }
            var stretch by remember { mutableStateOf(0f) }
            var angle by remember { mutableStateOf(0f) }
            var tilt by remember { mutableStateOf(0f) }
            var squish by remember { mutableStateOf(0f) }

            LaunchedEffect(maxX, maxY) {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9).coerceIn(1e-5, 0.05).toFloat()
                    last = now
                    if (!dragging && hypot(vel.x, vel.y) > 1f) {
                        var x = pos.x
                        var y = pos.y
                        x += vel.x * dt
                        y += vel.y * dt
                        if (x < 0f) { x = -x; vel = Offset(-vel.x * 0.6f, vel.y); squish = 0.35f }
                        if (x > maxX) { x = 2 * maxX - x; vel = Offset(-vel.x * 0.6f, vel.y); squish = 0.35f }
                        if (y < 0f) { y = -y; vel = Offset(vel.x, -vel.y * 0.6f); squish = 0.35f }
                        if (y > maxY) { y = 2 * maxY - y; vel = Offset(vel.x, -vel.y * 0.6f); squish = 0.35f }
                        pos = Offset(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
                        vel = vel * 0.985f
                    }
                    val speed = hypot(vel.x, vel.y)
                    val target = (speed / 2600f).coerceIn(0f, 0.40f)
                    stretch += (target - stretch) * 0.22f
                    if (speed > 1f) {
                        angle = Math.toDegrees(atan2(vel.y.toDouble(), vel.x.toDouble())).toFloat()
                    }
                    tilt += ((vel.x * 0.03f).coerceIn(-16f, 16f) - tilt) * 0.2f
                    squish *= 0.88f
                }
            }

            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Желейный CrystalN",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            "Хлопни и толкни логотип — он желейный.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CircleGlassButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        isDark = isDark,
                        onClick = onDismiss,
                    )
                }
            }

            Box(
                Modifier
                    .size(jellySize)
                    .graphicsLayer {
                        translationX = pos.x
                        translationY = pos.y
                        val s = stretch + squish
                        val ca = abs(cos(Math.toRadians(angle.toDouble()))).toFloat()
                        val sa = abs(sin(Math.toRadians(angle.toDouble()))).toFloat()
                        scaleX = 1f + s * ca - s * 0.30f * sa
                        scaleY = 1f + s * sa - s * 0.30f * ca
                        rotationZ = tilt
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                vel = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                vel = Offset(dragAmount.x / 0.016f, dragAmount.y / 0.016f)
                                pos = Offset(
                                    (pos.x + dragAmount.x).coerceIn(0f, maxX),
                                    (pos.y + dragAmount.y).coerceIn(0f, maxY),
                                )
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF6C4FF6), Color(0xFF9D4FF6))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val logo = remember { resourcePainter("crystaln/icon.png") }
                if (logo != null) {
                    Image(
                        painter = logo,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp),
                    )
                } else {
                    Icon(
                        Icons.Default.Lan,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        }
    }
}

/** A rounded glass panel holding the rows of one settings section. */
@Composable
internal fun SettingsPanel(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(26.dp), Glass.tint(isDark, false), isDark)
            .padding(18.dp),
    ) {
        content()
    }
}

