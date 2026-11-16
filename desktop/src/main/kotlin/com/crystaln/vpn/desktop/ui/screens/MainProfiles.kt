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

/** Section header with the count badge and the add / import buttons. */
@Composable
internal fun ProfilesHeader(
    count: Int,
    isDark: Boolean,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    pingAllRunning: Boolean = false,
    onPingAll: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Профили",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        CountBadge(count)
        Spacer(Modifier.weight(1f))
        CircleGlassButton(
            icon = Icons.Default.NetworkCheck,
            contentDescription = "Ping all servers",
            isDark = isDark,
            onClick = onPingAll,
            size = 40.dp,
            enabled = count > 0 && !pingAllRunning,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(
            icon = Icons.Default.FileOpen,
            contentDescription = "Import from file",
            isDark = isDark,
            onClick = onImport,
            size = 40.dp,
        )
        Spacer(Modifier.width(8.dp))
        CircleGlassButton(
            icon = Icons.Default.Add,
            contentDescription = "Add profile",
            isDark = isDark,
            accent = true,
            onClick = onAdd,
            size = 40.dp,
        )
    }
}

@Composable
internal fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    isDark: Boolean,
    ping: Long?,
    pingable: Boolean,
    onPing: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
    totalTraffic: TrafficStats? = null,
) {
    val pc = protocolColor(profile.protocol)
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Glass.tint(isDark, false),
                isDark,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(pc.copy(alpha = 0.30f), pc.copy(alpha = 0.12f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lan,
                    contentDescription = null,
                    tint = pc,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Protocol + summary share one line so the right-side actions
                // never squeeze the text on narrow screens.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.protocol.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = pc,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        profile.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                totalTraffic?.takeIf { it.rx + it.tx > 0 }?.let { t ->
                    Text(
                        "Всего: ${formatBytes(t.rx + t.tx)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            if (pingable) {
                PingChip(ping = ping, tint = pc, onPing = onPing)
                Spacer(Modifier.width(4.dp))
            }
            if (selected) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            // Compact icon buttons: the default 48dp target triples the
            // footprint of three actions and starves the text column.
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Small chip showing the measured server ping for a profile: a "measure"
 * icon before the first attempt, "—" in error color when unreachable,
 * "N мс" otherwise. Tap (re)measures.
 */
@Composable
internal fun PingChip(
    ping: Long?,
    tint: Color,
    onPing: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.16f))
            .clickable(onClick = onPing)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ping == null -> Icon(
                Icons.Default.NetworkCheck,
                contentDescription = "Ping",
                tint = tint,
                modifier = Modifier.size(15.dp),
            )

            ping < 0 -> Text(
                "—",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )

            else -> Text(
                "$ping мс",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

/** TCP-connect ping is only meaningful for Xray-family TCP transports. */
fun isPingable(p: Profile): Boolean {
    if (p.isWireGuardFamily || p.isOpenVpn) return false
    if (p.address.isEmpty() || p.port <= 0) return false
    return (p.fields["network"] ?: "tcp") !in setOf("quic", "kcp", "udp")
}

@Composable
internal fun EmptyProfiles(
    isDark: Boolean,
    onImport: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(22.dp), Glass.tint(isDark, false), isDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Нет профилей",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Импортируйте конфиг из файла или буфера обмена либо создайте вручную",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "Импорт",
                icon = Icons.Default.FileOpen,
                accent = false,
                isDark = isDark,
                onClick = onImport,
            )
            PillButton(
                text = "Создать",
                icon = Icons.Default.Add,
                accent = true,
                isDark = isDark,
                onClick = onAdd,
            )
        }
    }
}
