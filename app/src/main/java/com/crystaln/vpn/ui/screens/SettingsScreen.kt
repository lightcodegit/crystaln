package com.crystaln.vpn.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.BuildConfig
import com.crystaln.vpn.R
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.Subscription
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.net.TcpPing
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassDivider
import com.crystaln.vpn.ui.glass.GlassSegmented
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.PillButton
import kotlinx.coroutines.launch
import com.crystaln.vpn.ui.glass.SectionLabel
import com.crystaln.vpn.ui.glass.liquidGlass
import com.crystaln.vpn.ui.rememberWallpaper
import com.crystaln.vpn.ui.theme.CrystalPalettes
import com.crystaln.vpn.ui.theme.LocalCrystalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun SettingsScreen(
    isDark: Boolean,
    themeMode: SettingsStore.ThemeMode,
    onThemeChange: (SettingsStore.ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    paletteId: String,
    onPaletteChange: (String) -> Unit,
    gyroBackground: Boolean,
    onGyroBackgroundChange: (Boolean) -> Unit,
    liquidBackground: Boolean,
    onLiquidBackgroundChange: (Boolean) -> Unit,
    amoledMode: Boolean,
    onAmoledModeChange: (Boolean) -> Unit,
    appLock: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    autoFastest: Boolean,
    onAutoFastestChange: (Boolean) -> Unit,
    dnsViaTunnel: Boolean,
    onDnsViaTunnelChange: (Boolean) -> Unit,
    dnsServer: String,
    onDnsServerChange: (String) -> Unit,
    blockAds: Boolean,
    onBlockAdsChange: (Boolean) -> Unit,
    anonymizeNames: Boolean,
    onAnonymizeNamesChange: (Boolean) -> Unit,
    checkUrl: String,
    onCheckUrlChange: (String) -> Unit,
    checkTimeoutSec: Int,
    onCheckTimeoutSecChange: (Int) -> Unit,
    blockAdsBusy: Boolean,
    subscriptions: List<Subscription>,
    subscriptionsBusy: Boolean,
    subscriptionBusyId: String?,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onRefreshAllSubscriptions: () -> Unit,
    onToggleSubscription: (Subscription, Boolean) -> Unit,
    onRemoveSubscription: (Subscription) -> Unit,
    wallpaperSet: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    val wallpaper = rememberWallpaper(SettingsStore.wallpaper(LocalContext.current))
    val glassDark = wallpaper?.isDark ?: isDark
    val overWallpaper = wallpaper != null
    var addSubscriptionVisible by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var checkUrlText by remember { mutableStateOf(checkUrl) }
    var timeoutText by remember(checkTimeoutSec) { mutableStateOf(checkTimeoutSec.toString()) }
    var jellyGame by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val hazeState = rememberHazeState()

        // Everything drawn here can be blurred by floating glass above.
        Box(Modifier.matchParentSize().hazeSource(hazeState)) {
            if (wallpaper != null) {
                WallpaperLayer(wallpaper.bitmap, wallpaper.isDark, themeDark = isDark)
            } else {
                LiquidBlobs(isDark)
            }

            // Content colors follow the user's chosen theme (not the photo).
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                ) {
                Spacer(Modifier.height(10.dp))

                // Header: glass back button + big title.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleGlassButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        isDark = glassDark,
                        overWallpaper = overWallpaper,
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.tab_settings),
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
                    SectionLabel(stringResource(R.string.section_appearance))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        Text(
                            stringResource(R.string.theme_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassSegmented(
                            options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                        ),
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
                            overWallpaper = overWallpaper,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.dynamic_color_title),
                            subtitle = stringResource(R.string.dynamic_color_sub),
                            checked = dynamicColor,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onChange = onDynamicColorChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        Text(
                            stringResource(R.string.palette_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        PalettePicker(
                            paletteId = paletteId,
                            onPaletteChange = onPaletteChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.gyro_title),
                            subtitle = stringResource(R.string.gyro_sub),
                            checked = gyroBackground,
                            onChange = onGyroBackgroundChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.liquid_title),
                            subtitle = stringResource(R.string.liquid_sub),
                            checked = liquidBackground,
                            onChange = onLiquidBackgroundChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.amoled_title),
                            subtitle = stringResource(R.string.amoled_sub),
                            checked = amoledMode,
                            onChange = onAmoledModeChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.applock_title),
                            subtitle = stringResource(R.string.applock_sub),
                            checked = appLock,
                            onChange = onAppLockChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        Text(
                            stringResource(R.string.wallpaper_title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (wallpaperSet) stringResource(R.string.wallpaper_set) else stringResource(R.string.wallpaper_unset),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = stringResource(R.string.wallpaper_pick),
                                icon = Icons.Default.Image,
                                accent = true,
                                isDark = glassDark,
                                onClick = onPickWallpaper,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = stringResource(R.string.wallpaper_reset),
                                icon = Icons.Default.Wallpaper,
                                accent = false,
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                enabled = wallpaperSet,
                                onClick = onClearWallpaper,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── Connection ─────────────────────────────────────
                    SectionLabel(stringResource(R.string.tab_connect))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        SwitchRow(
                            title = stringResource(R.string.autoconnect_title),
                            subtitle = stringResource(R.string.autoconnect_sub),
                            checked = autoConnect,
                            onChange = onAutoConnectChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.auto_fastest_title),
                            subtitle = stringResource(R.string.auto_fastest_sub),
                            checked = autoFastest,
                            onChange = onAutoFastestChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        OutlinedTextField(
                            value = checkUrlText,
                            onValueChange = { checkUrlText = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.check_url_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = timeoutText,
                            onValueChange = { v ->
                                timeoutText = v
                                v.toIntOrNull()?.let { onCheckTimeoutSecChange(it) }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.check_timeout_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.check_timeout_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        PillButton(
                            text = stringResource(R.string.diag_title),
                            icon = Icons.Default.Bolt,
                            accent = false,
                            isDark = glassDark,
                            overWallpaper = overWallpaper,
                            onClick = onDiagnostics,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // ── Privacy ────────────────────────────────────────
                    SectionLabel(stringResource(R.string.privacy_title))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        SwitchRow(
                            title = stringResource(R.string.dns_tunnel_title),
                            subtitle = stringResource(R.string.dns_tunnel_sub),
                            checked = dnsViaTunnel,
                            onChange = onDnsViaTunnelChange,
                        )
                        if (dnsViaTunnel) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = dnsServer,
                                onValueChange = onDnsServerChange,
                                singleLine = true,
                                label = { Text(stringResource(R.string.dns_server_label)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.block_ads_title),
                            subtitle = stringResource(R.string.block_ads_sub),
                            checked = blockAds,
                            enabled = !blockAdsBusy,
                            onChange = onBlockAdsChange,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = stringResource(R.string.anon_names_title),
                            subtitle = stringResource(R.string.anon_names_sub),
                            checked = anonymizeNames,
                            onChange = onAnonymizeNamesChange,
                        )
                    }

                    // ── Subscriptions ───────────────────────────────────
                    SectionLabel(stringResource(R.string.subscriptions_title))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        if (subscriptions.isEmpty()) {
                            Text(
                                stringResource(R.string.subscriptions_empty),
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
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            sub.lastError?.takeIf { !sub.enabled || !sub.autoRefresh || it.isNotBlank() }
                                ?.let { error ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 3,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PillButton(
                                    text = stringResource(R.string.subscriptions_refresh),
                                    icon = Icons.Default.Refresh,
                                    accent = true,
                                    isDark = glassDark,
                                    overWallpaper = overWallpaper,
                                    enabled = !subscriptionsBusy &&
                                        subscriptionBusyId?.let { it != sub.id } ?: true,
                                    onClick = { onRefreshSubscription(sub) },
                                    modifier = Modifier.weight(1f),
                                )
                                PillButton(
                                    text = stringResource(R.string.subscriptions_delete),
                                    icon = null,
                                    accent = false,
                                    isDark = glassDark,
                                    overWallpaper = overWallpaper,
                                    enabled = !subscriptionsBusy,
                                    onClick = { onRemoveSubscription(sub) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (subscriptions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.subscriptions_auto_refresh),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = stringResource(R.string.subscriptions_add),
                                icon = Icons.Default.Add,
                                accent = true,
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                enabled = !subscriptionsBusy,
                                onClick = { addSubscriptionVisible = true },
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = stringResource(R.string.subscriptions_refresh_all),
                                icon = Icons.Default.Refresh,
                                accent = false,
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                enabled = !subscriptionsBusy && subscriptions.any { it.enabled },
                                onClick = onRefreshAllSubscriptions,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── Tools: standalone ping test ────────────────────
                    SectionLabel(stringResource(R.string.section_tools))
                    PingToolPanel(isDark = glassDark, overWallpaper = overWallpaper)

                    // ── Data ───────────────────────────────────────────
                    SectionLabel(stringResource(R.string.section_data))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        Text(
                            stringResource(R.string.data_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = stringResource(R.string.action_import),
                                icon = Icons.Default.FileOpen,
                                accent = true,
                                isDark = glassDark,
                                onClick = onImport,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = stringResource(R.string.action_export),
                                icon = Icons.Default.FileDownload,
                                accent = false,
                                isDark = glassDark,
                                overWallpaper = overWallpaper,
                                onClick = onExport,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── About ──────────────────────────────────────────
                    SectionLabel(stringResource(R.string.section_about))
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CrystalN",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.about_cores),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Hidden easter egg: the jelly logo mini game.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircleGlassButton(
                            icon = Icons.Default.SportsEsports,
                            contentDescription = "CrystalN jelly",
                            isDark = glassDark,
                            overWallpaper = overWallpaper,
                            size = 40.dp,
                            onClick = { jellyGame = true },
                        )
                    }

                    // Clearance so the last rows sit above the tab bar;
                    // content under the bar is genuinely frosted by Haze.
                    Spacer(Modifier.height(170.dp))
                }
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

        // Floating liquid-glass tab bar, iOS-style — blurs the real
        // content behind it via Haze.
        GlassTabBar(
            hazeState = hazeState,
            isDark = glassDark,
            overWallpaper = overWallpaper,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, stringResource(R.string.tab_connect)),
                GlassTab(Icons.Default.Settings, stringResource(R.string.tab_settings)),
            ),
            selected = 1,
            onSelect = { index -> if (index == 0) onBack() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )

        if (jellyGame) {
            JellyGameOverlay(onDismiss = { jellyGame = false })
        }
    }
}

/** A rounded glass panel holding the rows of one settings section. */
@Composable
internal fun SettingsPanel(
    isDark: Boolean,
    overWallpaper: Boolean,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(26.dp), Glass.tint(isDark, overWallpaper), isDark)
            .padding(18.dp),
    ) {
        content()
    }
}

/** Horizontal row of round gradient swatches with a selection ring. */
@Composable
private fun PalettePicker(
    paletteId: String,
    onPaletteChange: (String) -> Unit,
) {
    val selected = CrystalPalettes.firstOrNull { it.id == paletteId } ?: CrystalPalettes.first()
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        CrystalPalettes.forEach { palette ->
            val isSel = palette.id == selected.id
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSel) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPaletteChange(palette.id) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isSel) 34.dp else 38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(palette.swatch, lerp(palette.swatch, palette.accent2, 0.7f)),
                            ),
                        ),
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.import_selected, selected.name),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun subscriptionStatusText(sub: Subscription): String = when (sub.lastResult) {
    null -> if (sub.lastUpdated > 0L) stringResource(R.string.subscriptions_never_checked)
    else stringResource(R.string.subscriptions_never_checked)
    true -> stringResource(
        R.string.subscriptions_updated,
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
        ).format(java.util.Date(sub.lastUpdated)),
    )
    false -> stringResource(R.string.subscriptions_failed)
}

@Composable
private fun AddSubscriptionDialog(
    initialUrl: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val valid = SubscriptionsStore.normalizeUrl(url) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscriptions_add)) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                isError = url.isNotBlank() && !valid,
                placeholder = { Text(stringResource(R.string.subscriptions_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid && !busy,
                onClick = { onAdd(url) },
            ) {
                Text(stringResource(R.string.subscriptions_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
        )
    }
}

/**
 * Standalone ping tool: type any site or IP (optionally host:port) and get
 * the TCP-connect latency. Runs on Dispatchers.IO, independent of the VPN
 * state and of any profile.
 */
@Composable
private fun PingToolPanel(isDark: Boolean, overWallpaper: Boolean) {
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    /** Measured latency + port, or null while idle; -1 = unreachable. */
    var result by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val scope = rememberCoroutineScope()

    SettingsPanel(isDark = isDark, overWallpaper = overWallpaper) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ping_tool_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = stringResource(R.string.ping_tool_run),
                icon = Icons.Default.NetworkCheck,
                accent = true,
                isDark = isDark,
                enabled = input.isNotBlank() && !running,
                onClick = {
                    val target = TcpPing.parse(input) ?: return@PillButton
                    running = true
                    result = null
                    scope.launch {
                        val ms = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            TcpPing.measure(target.first, target.second)
                        }
                        result = ms to target.second
                        running = false
                    }
                },
            )
            Spacer(Modifier.width(12.dp))
            when {
                running -> Text(
                    stringResource(R.string.ping_tool_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                result != null && result!!.first < 0 -> Text(
                    stringResource(R.string.ping_tool_fail),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )

                result != null -> Text(
                    stringResource(R.string.ping_tool_ok, result!!.first, result!!.second),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                else -> Text(
                    stringResource(R.string.ping_tool_idle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
