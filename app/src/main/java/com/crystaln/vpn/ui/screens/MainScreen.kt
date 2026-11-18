package com.crystaln.vpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.CountBadge
import com.crystaln.vpn.ui.glass.liquidGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.crystaln.vpn.ui.rememberWallpaper
import com.crystaln.vpn.ui.theme.LocalCrystalPalette

fun protocolColor(p: Protocol): Color = when (p) {
    Protocol.VMESS -> Color(0xFF4F6AF6)
    Protocol.VLESS -> Color(0xFF6C4FF6)
    Protocol.TROJAN -> Color(0xFFE85454)
    Protocol.SHADOWSOCKS -> Color(0xFF3FBF7F)
    Protocol.SOCKS -> Color(0xFF8A93A6)
    Protocol.HTTP, Protocol.HTTPS -> Color(0xFFE8A238)
    Protocol.WIREGUARD -> Color(0xFFE86A4B)
    Protocol.AMNEZIAWG -> Color(0xFFC95E3F)
    Protocol.OPENVPN -> Color(0xFFC9A23F)
    Protocol.HYSTERIA2 -> Color(0xFFB04FF6)
    Protocol.TUIC -> Color(0xFF3FA8BF)
    Protocol.ANYTLS -> Color(0xFFBF6A3F)
    Protocol.HYSTERIA1 -> Color(0xFFBF3F6A)
}

/**
 * The "Connect" tab: header, the big status hero with the connect ring and
 * a glass entry card into the profiles tab. The profile list itself lives
 * in [ProfilesScreen].
 */
@Composable
fun MainScreen(
    isDark: Boolean,
    state: VpnManager.UiState,
    profileCount: Int,
    selected: Profile?,
    /** Anonymized display name (flag + index) when the privacy mask is on. */
    selectedLabel: String? = null,
    wallpaperPath: String? = null,
    rxHistory: List<Long> = emptyList(),
    txHistory: List<Long> = emptyList(),
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    onProfiles: () -> Unit,
) {
    val wallpaper = rememberWallpaper(wallpaperPath)
    val overWallpaper = wallpaper != null
    // Glass adapts to the photo: dark wallpaper gets dark glass with a
    // light rim, light wallpaper gets frosted white glass.
    val glassDark = wallpaper?.isDark ?: isDark

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

            // Content colors follow the user's chosen theme (not the photo): a
            // dark theme gets light text, a light one dark text. Glass itself
            // still adapts to the wallpaper via glassDark.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 170.dp),
                ) {
                item { Header(state = state, dark = glassDark) }

                item {
                    HeroStatus(
                        state = state,
                        selected = selected,
                        selectedLabel = selectedLabel,
                        isDark = glassDark,
                        overWallpaper = overWallpaper,
                        rxHistory = rxHistory,
                        txHistory = txHistory,
                        onConnect = onConnect,
                    )
                }

                item {
                    ProfilesEntry(
                        count = profileCount,
                        isDark = glassDark,
                        overWallpaper = overWallpaper,
                        onClick = onProfiles,
                    )
                }
                }
            }
        }

        // Floating liquid-glass tab bar, iOS-style — blurs the real
        // content behind it via Haze.
        GlassTabBar(
            hazeState = hazeState,
            isDark = glassDark,
            overWallpaper = overWallpaper,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, stringResource(R.string.tab_connect)),
                GlassTab(Icons.Default.Lan, stringResource(R.string.tab_profiles)),
                GlassTab(Icons.Default.Settings, stringResource(R.string.tab_settings)),
            ),
            selected = 0,
            onSelect = { index ->
                when (index) {
                    1 -> onProfiles()
                    2 -> onSettings()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

/** Glass card that opens the profiles tab: icon, title, count badge, chevron. */
@Composable
private fun ProfilesEntry(
    count: Int,
    isDark: Boolean,
    overWallpaper: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(shape, Glass.tint(isDark, overWallpaper), isDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.tab_profiles),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        CountBadge(count)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** App identity row: gradient logo mark, name, live status chip. */
@Composable
private fun Header(state: VpnManager.UiState, dark: Boolean) {
    val palette = LocalCrystalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(palette.gradient(dark))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.weight(1f))
        StatusChip(state)
    }
}

/** Small colored dot + label for the tunnel state. */
@Composable
private fun StatusChip(state: VpnManager.UiState) {
    val (label, color) = when (state.state) {
        VpnManager.State.OFF ->
            stringResource(R.string.state_short_off) to com.crystaln.vpn.ui.theme.StatusOff
        VpnManager.State.STARTING ->
            stringResource(R.string.state_short_starting) to com.crystaln.vpn.ui.theme.StatusConnecting
        VpnManager.State.CONNECTED -> "VPN" to com.crystaln.vpn.ui.theme.StatusConnected
        VpnManager.State.STOPPING ->
            stringResource(R.string.state_short_stopping) to com.crystaln.vpn.ui.theme.StatusConnecting
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/**
 * The hero block: the connect ring, the headline state, the selected
 * profile pill and live traffic stat cards while connected.
 */
@Composable
private fun HeroStatus(
    state: VpnManager.UiState,
    selected: Profile?,
    selectedLabel: String?,
    isDark: Boolean,
    overWallpaper: Boolean,
    rxHistory: List<Long> = emptyList(),
    txHistory: List<Long> = emptyList(),
    onConnect: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectRing(state = state, isDark = isDark, onConnect = onConnect)

        Spacer(Modifier.height(12.dp))

        val (headline, sub, color) = when (state.state) {
            VpnManager.State.OFF -> Triple(
                stringResource(R.string.state_off),
                stringResource(R.string.hero_tap_connect),
                MaterialTheme.colorScheme.onSurface,
            )
            VpnManager.State.STARTING -> Triple(
                stringResource(R.string.state_starting),
                if (state.checking) stringResource(R.string.hero_checking_sub) else stringResource(R.string.hero_starting_sub),
                MaterialTheme.colorScheme.primary,
            )
            VpnManager.State.CONNECTED -> Triple(
                stringResource(R.string.state_connected),
                stringResource(R.string.hero_connected_sub),
                com.crystaln.vpn.ui.theme.StatusConnected,
            )
            VpnManager.State.STOPPING -> Triple(
                stringResource(R.string.state_stopping),
                stringResource(R.string.hero_stopping_sub),
                MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            sub,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // A little welcome line once the tunnel is live — fades and slides in.
        if (state.state == VpnManager.State.CONNECTED) {
            var welcomeShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { welcomeShown = true }
            AnimatedVisibility(
                visible = welcomeShown,
                enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 2 },
            ) {
                Text(
                    stringResource(R.string.welcome_free_internet),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = com.crystaln.vpn.ui.theme.StatusConnected,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // Live speed sparkline while connected.
        if (state.state == VpnManager.State.CONNECTED && (rxHistory.isNotEmpty() || txHistory.isNotEmpty())) {
            val max = (rxHistory + txHistory).maxOrNull()?.coerceAtLeast(1L) ?: 1L
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(top = 8.dp),
            ) {
                val w = size.width
                val h = size.height
                fun draw(series: List<Long>, color: androidx.compose.ui.graphics.Color) {
                    if (series.size < 2) return
                    val step = w / (series.size - 1).coerceAtLeast(1)
                    val path = androidx.compose.ui.graphics.Path()
                    series.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - (v.toFloat() / max * h * 0.9f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
                draw(rxHistory, androidx.compose.ui.graphics.Color(0xFF4F6AF6))
                draw(txHistory, androidx.compose.ui.graphics.Color(0xFF3FBF7F))
            }
        }

        selected?.let { p ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .liquidGlass(
                        RoundedCornerShape(999.dp),
                        Glass.tintSoft(isDark, overWallpaper),
                        isDark,
                        flat = true,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(protocolColor(p.protocol)))
                Spacer(Modifier.width(7.dp))
                Text(
                    selectedLabel ?: p.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "· ${p.protocol.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        if (state.state == VpnManager.State.CONNECTED && (state.rx > 0 || state.tx > 0)) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    label = stringResource(R.string.stat_download),
                    value = humanBytes(state.rx),
                    icon = Icons.Default.ArrowDownward,
                    tint = MaterialTheme.colorScheme.tertiary,
                    isDark = isDark,
                    overWallpaper = overWallpaper,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.stat_upload),
                    value = humanBytes(state.tx),
                    icon = Icons.Default.ArrowUpward,
                    tint = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
                    overWallpaper = overWallpaper,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.error?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Big circular gauge: a gradient arc fills as the tunnel comes up, spins
 * while connecting, and a soft glow pulses while active. The center is a
 * glass power button.
 */
@Composable
private fun ConnectRing(
    state: VpnManager.UiState,
    isDark: Boolean,
    onConnect: () -> Unit,
) {
    val palette = LocalCrystalPalette.current
    val active = state.state != VpnManager.State.OFF
    val busy = state.state == VpnManager.State.STARTING || state.state == VpnManager.State.STOPPING
    val connected = state.state == VpnManager.State.CONNECTED

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(216.dp)) {
        if (active) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.22f,
                animationSpec = infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
                label = "scale",
            )
            val glowAlpha by pulse.animateFloat(
                initialValue = 0.30f,
                targetValue = 0.0f,
                animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
                label = "glow",
            )
            Box(
                Modifier
                    .size(196.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = glowAlpha
                    }
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(palette.accent2, Color.Transparent))),
            )
        }

        // The gauge arc.
        if (busy) {
            val spin = rememberInfiniteTransition(label = "spin")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label = "angle",
            )
            RingCanvas(track = isDark, arcBrush = Brush.sweepGradient(listOf(palette.accent, palette.accent2, palette.accent)), start = angle, sweep = 110f)
        } else if (connected) {
            RingCanvas(track = isDark, arcBrush = Brush.sweepGradient(listOf(palette.accent, palette.accent2, palette.accent)), start = -90f, sweep = 360f)
        } else {
            RingCanvas(track = isDark, arcBrush = null, start = 0f, sweep = 0f)
        }

        // Center power button.
        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .then(
                    if (active) {
                        Modifier.background(Brush.linearGradient(palette.gradient(isDark)))
                    } else {
                        Modifier
                            .background(Glass.tintSoft(isDark, false))
                            .border(1.dp, Glass.rim(isDark), CircleShape)
                    },
                )
                .clickable(onClick = onConnect),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp), strokeWidth = 4.dp)
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = if (active) "Disconnect" else "Connect",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
    }
}

/** Track ring + optional gradient arc, both with rounded caps. */
@Composable
private fun RingCanvas(
    track: Boolean,
    arcBrush: Brush?,
    start: Float,
    sweep: Float,
) {
    val trackColor = if (track) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    Canvas(Modifier.size(216.dp)) {
        val stroke = 9.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (arcBrush != null && sweep > 0f) {
            drawArc(
                brush = arcBrush,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    isDark: Boolean,
    overWallpaper: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .liquidGlass(RoundedCornerShape(18.dp), Glass.tint(isDark, overWallpaper), isDark)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun humanBytes(v: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var x = v.toDouble()
    var u = 0
    while (x >= 1024 && u < units.size - 1) {
        x /= 1024
        u++
    }
    return "%.1f %s".format(x, units[u])
}
