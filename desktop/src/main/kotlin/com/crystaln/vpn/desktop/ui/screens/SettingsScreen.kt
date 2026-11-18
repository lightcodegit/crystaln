package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.desktop.ui.LiquidBlobs
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
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
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
    onDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    val glassDark = isDark
    var addSubscriptionVisible by remember { mutableStateOf(false) }

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
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        PillButton(
                            text = "Диагностика",
                            icon = Icons.Default.Bolt,
                            accent = false,
                            isDark = glassDark,
                            onClick = onDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Kill-switch",
                            subtitle = "При падении VPN блокировать трафик, кроме VPN-интерфейса (нужны права root/администратора)",
                            checked = killSwitch,
                            onChange = onKillSwitchChange,
                        )
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
                    }

                    // ── About ──────────────────────────────────────────
                    SectionLabel("О приложении")
                    SettingsPanel(isDark = glassDark) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CrystalN",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
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

                    // Clearance so the last rows sit above the tab bar.
                    Spacer(Modifier.height(170.dp))
                }
            }
        }

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
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 1,
            onSelect = { index -> if (index == 0) onBack() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
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

