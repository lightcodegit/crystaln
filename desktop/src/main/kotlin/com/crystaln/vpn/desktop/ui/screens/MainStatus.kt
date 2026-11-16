package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileShareLink
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.data.TrafficStats
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.ui.glass.CircleGlassButton
import com.crystaln.vpn.desktop.ui.glass.CountBadge
import com.crystaln.vpn.desktop.ui.glass.Glass
import com.crystaln.vpn.desktop.ui.glass.GlassTab
import com.crystaln.vpn.desktop.ui.glass.GlassTabBar
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.liquidGlass
import com.crystaln.vpn.desktop.resourcePainter
import com.crystaln.vpn.desktop.ui.theme.LocalCrystalPalette
import com.crystaln.vpn.desktop.ui.theme.StatusConnected
import com.crystaln.vpn.desktop.ui.theme.StatusConnecting
import com.crystaln.vpn.desktop.ui.theme.StatusOff
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/** App identity row: gradient logo mark, name, live status chip. */
@Composable
internal fun Header(state: TunnelManager.UiState, dark: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(LocalCrystalPalette.current.gradient(dark))),
            contentAlignment = Alignment.Center,
        ) {
            val mark = remember { resourcePainter("crystaln/icon-mark.png") }
            if (mark != null) {
                Image(
                    painter = mark,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                Text("C", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            "CrystalN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.weight(1f))
        StatusChip(state)
    }
}

/** Compact status indicator in the header. */
@Composable
internal fun StatusChip(state: TunnelManager.UiState) {
    val (label, color) = when (state.state) {
        TunnelManager.State.OFF -> "Выкл" to StatusOff
        TunnelManager.State.STARTING -> "Подкл…" to StatusConnecting
        TunnelManager.State.CONNECTED -> "VPN" to StatusConnected
        TunnelManager.State.STOPPING -> "Откл…" to StatusConnecting
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
 * The hero: a large status ring with the power button in the middle, the
 * headline below it, the selected profile chip and live traffic stats.
 */
@Composable
internal fun HeroStatus(
    state: TunnelManager.UiState,
    selected: Profile?,
    isDark: Boolean,
    onConnect: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectRing(state = state, isDark = isDark, onConnect = onConnect)

        Spacer(Modifier.height(12.dp))

        val (headline, sub, color) = when (state.state) {
            TunnelManager.State.OFF -> Triple(
                "Отключено",
                "Нажмите, чтобы подключиться",
                MaterialTheme.colorScheme.onSurface,
            )
            TunnelManager.State.STARTING -> Triple(
                "Подключение…",
                "Устанавливаем защищённый туннель",
                MaterialTheme.colorScheme.primary,
            )
            TunnelManager.State.CONNECTED -> Triple(
                "Подключено",
                "Трафик идёт через защищённый туннель",
                StatusConnected,
            )
            TunnelManager.State.STOPPING -> Triple(
                "Отключение…",
                "Закрываем туннель",
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

        selected?.let { p ->
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .liquidGlass(
                        RoundedCornerShape(999.dp),
                        Glass.tintSoft(isDark, false),
                        isDark,
                        flat = true,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(protocolColor(p.protocol)))
                Spacer(Modifier.width(7.dp))
                Text(
                    p.name,
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

        if (state.state == TunnelManager.State.CONNECTED && (state.rx > 0 || state.tx > 0)) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    label = "Загрузка",
                    value = humanBytes(state.rx),
                    icon = Icons.Default.ArrowDownward,
                    tint = MaterialTheme.colorScheme.tertiary,
                    isDark = isDark,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Отдача",
                    value = humanBytes(state.tx),
                    icon = Icons.Default.ArrowUpward,
                    tint = MaterialTheme.colorScheme.primary,
                    isDark = isDark,
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
internal fun ConnectRing(
    state: TunnelManager.UiState,
    isDark: Boolean,
    onConnect: () -> Unit,
) {
    val palette = LocalCrystalPalette.current
    val active = state.state != TunnelManager.State.OFF
    val busy = state.state == TunnelManager.State.STARTING || state.state == TunnelManager.State.STOPPING
    val connected = state.state == TunnelManager.State.CONNECTED

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(216.dp)) {
        if (active) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.22f,
                animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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
internal fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .liquidGlass(RoundedCornerShape(18.dp), Glass.tint(isDark, false), isDark)
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

