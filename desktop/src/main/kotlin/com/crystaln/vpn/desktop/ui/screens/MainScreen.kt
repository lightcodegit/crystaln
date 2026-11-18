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

fun protocolColor(p: Protocol): Color = when (p) {
    Protocol.VMESS -> Color(0xFF4F6AF6)
    Protocol.VLESS -> Color(0xFF7C4FF6)
    Protocol.TROJAN -> Color(0xFFE85A6A)
    Protocol.SHADOWSOCKS -> Color(0xFF38B6E8)
    Protocol.SOCKS -> Color(0xFF2BB673)
    Protocol.HTTP, Protocol.HTTPS -> Color(0xFFE8A238)
    Protocol.WIREGUARD -> Color(0xFFE8622C)
    Protocol.AMNEZIAWG -> Color(0xFFD04FF6)
    Protocol.OPENVPN -> Color(0xFF4F9E58)
    Protocol.HYSTERIA2 -> Color(0xFF8A4FF6)
    Protocol.TUIC -> Color(0xFF3FA8BF)
    Protocol.ANYTLS -> Color(0xFFBF6A3F)
    Protocol.HYSTERIA1 -> Color(0xFFBF3F6A)
}

@Composable
fun MainScreen(
    isDark: Boolean,
    state: TunnelManager.UiState,
    profiles: List<Profile>,
    selectedId: String?,
    /** Privacy mask: show "🇩🇪 #N" instead of profile names. */
    anonymizeNames: Boolean = false,
    onSelect: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    /** Measured TCP ping per profile id (ms), -1 = unreachable, absent = not measured. */
    pingResults: Map<String, Long> = emptyMap(),
    onPing: (Profile) -> Unit = {},
    pingAllRunning: Boolean = false,
    onPingAll: () -> Unit = {},
    /** Accumulated per-profile usage, id → totals. */
    trafficStats: Map<String, TrafficStats> = emptyMap(),
    /** Profile search filter. */
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    /** Subscription id → display name, for grouping. */
    subscriptionTitles: Map<String, String> = emptyMap(),
    /** Open the share dialog (link + QR) for a profile. */
    onShare: (Profile) -> Unit = {},
    shareProfile: Profile? = null,
    onDismissShare: () -> Unit = {},
) {
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

                Header(state = state, dark = isDark)

                Spacer(Modifier.height(8.dp))

                HeroStatus(
                    state = state,
                    selected = profiles.firstOrNull { it.id == (state.profile?.id ?: selectedId) },
                    profiles = profiles,
                    anonymizeNames = anonymizeNames,
                    isDark = isDark,
                    onConnect = onConnect,
                )

                Spacer(Modifier.height(14.dp))

                ProfilesHeader(
                    count = profiles.size,
                    isDark = isDark,
                    onImport = onImport,
                    onAdd = onAdd,
                    pingAllRunning = pingAllRunning,
                    onPingAll = onPingAll,
                )

                Spacer(Modifier.height(10.dp))

                if (profiles.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        placeholder = { Text("Поиск профилей") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                    )
                }

                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    if (profiles.isEmpty()) {
                        item {
                            EmptyProfiles(
                                isDark = isDark,
                                onImport = onImport,
                                onAdd = onAdd,
                            )
                        }
                    } else {
                        val query = searchQuery.trim()
                        val matched = { p: Profile ->
                            query.isEmpty() ||
                                p.name.contains(query, ignoreCase = true) ||
                                p.address.contains(query, ignoreCase = true) ||
                                p.protocol.displayName.contains(query, ignoreCase = true)
                        }
                        if (query.isNotEmpty()) {
                            items(profiles.filter(matched), key = { it.id }) { p ->
                                ProfileCard(
                                    profile = p,
                                    selected = p.id == selectedId,
                                    isDark = isDark,
                                    ping = pingResults[p.id],
                                    pingable = isPingable(p),
                                    onPing = { onPing(p) },
                                    onSelect = { onSelect(p) },
                                    onEdit = { onEdit(p) },
                                    onDelete = { onDelete(p) },
                                    onShare = { onShare(p) },
                                    totalTraffic = trafficStats[p.id],

                                    aliasLabel = if (anonymizeNames) com.crystaln.vpn.ui.ServerAlias.label(p, profiles.indexOf(p)) else null,
                                )
                            }
                        } else if (subscriptionTitles.isNotEmpty()) {
                            // Grouped: subscription groups first, then manual profiles.
                            val groups = subscriptionTitles.entries
                                .map { entry -> entry.key to profiles.filter { it.subscriptionId == entry.key } }
                                .filter { it.second.isNotEmpty() }
                            val manual = profiles.filter { it.subscriptionId == null }
                            groups.forEach { (title, list) ->
                                item(key = "group-$title") { SectionHeader(title) }
                                items(list, key = { it.id }) { p ->
                                    ProfileCard(
                                        profile = p,
                                        selected = p.id == selectedId,
                                        isDark = isDark,
                                        ping = pingResults[p.id],
                                        pingable = isPingable(p),
                                        onPing = { onPing(p) },
                                        onSelect = { onSelect(p) },
                                        onEdit = { onEdit(p) },
                                        onDelete = { onDelete(p) },
                                        onShare = { onShare(p) },
                                        totalTraffic = trafficStats[p.id],

                                        aliasLabel = if (anonymizeNames) com.crystaln.vpn.ui.ServerAlias.label(p, profiles.indexOf(p)) else null,
                                    )
                                }
                            }
                            if (manual.isNotEmpty()) {
                                item(key = "group-manual") {
                                    SectionHeader("Вручную")
                                }
                                items(manual, key = { it.id }) { p ->
                                    ProfileCard(
                                        profile = p,
                                        selected = p.id == selectedId,
                                        isDark = isDark,
                                        ping = pingResults[p.id],
                                        pingable = isPingable(p),
                                        onPing = { onPing(p) },
                                        onSelect = { onSelect(p) },
                                        onEdit = { onEdit(p) },
                                        onDelete = { onDelete(p) },
                                        onShare = { onShare(p) },
                                        totalTraffic = trafficStats[p.id],

                                        aliasLabel = if (anonymizeNames) com.crystaln.vpn.ui.ServerAlias.label(p, profiles.indexOf(p)) else null,
                                    )
                                }
                            }
                        } else {
                            items(profiles.filter(matched), key = { it.id }) { p ->
                                ProfileCard(
                                    profile = p,
                                    selected = p.id == selectedId,
                                    isDark = isDark,
                                    ping = pingResults[p.id],
                                    pingable = isPingable(p),
                                    onPing = { onPing(p) },
                                    onSelect = { onSelect(p) },
                                    onEdit = { onEdit(p) },
                                    onDelete = { onDelete(p) },
                                    onShare = { onShare(p) },
                                    totalTraffic = trafficStats[p.id],

                                    aliasLabel = if (anonymizeNames) com.crystaln.vpn.ui.ServerAlias.label(p, profiles.indexOf(p)) else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating liquid-glass tab bar, iOS-style.
        GlassTabBar(
            isDark = isDark,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, "Подключение"),
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 0,
            onSelect = { index -> if (index == 1) onSettings() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )

        shareProfile?.let { profile ->
            ShareProfileDialog(profile = profile, onDismiss = onDismissShare)
        }
    }
}

/** Small grey caption above a profile group. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
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

/** Localized byte size for the per-profile totals row. */
fun formatBytes(v: Long): String = when {
    v >= 1L shl 40 -> "%.2f ТБ".format(v / 1099511627776.0)
    v >= 1L shl 30 -> "%.2f ГБ".format(v / 1073741824.0)
    v >= 1L shl 20 -> "%.1f МБ".format(v / 1048576.0)
    else -> "%.0f КБ".format(v / 1024.0)
}

