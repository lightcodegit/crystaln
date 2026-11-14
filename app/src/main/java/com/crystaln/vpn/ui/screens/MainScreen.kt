package com.crystaln.vpn.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.TabBarBackdrop
import com.crystaln.vpn.ui.glass.liquidGlass
import com.crystaln.vpn.ui.glass.liquidSheen
import com.crystaln.vpn.ui.glass.rememberBreath
import com.crystaln.vpn.ui.rememberWallpaper
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.theme.LocalCrystalPalette

fun protocolColor(p: Protocol): Color = when (p) {
    Protocol.VMESS -> Color(0xFF4F6AF6)
    Protocol.VLESS -> Color(0xFF7C4FF6)
    Protocol.TROJAN -> Color(0xFFE85A6A)
    Protocol.SHADOWSOCKS -> Color(0xFF38B6E8)
    Protocol.SOCKS -> Color(0xFF2BB673)
    Protocol.HTTP -> Color(0xFFE8A238)
    Protocol.WIREGUARD -> Color(0xFFE8622C)
    Protocol.AMNEZIAWG -> Color(0xFFD04FF6)
}

@Composable
fun MainScreen(
    isDark: Boolean,
    state: VpnManager.UiState,
    profiles: List<Profile>,
    selectedId: String?,
    wallpaperPath: String? = null,
    onSelect: (Profile) -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
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
        if (wallpaper != null) {
            WallpaperLayer(wallpaper.bitmap, wallpaper.isDark)
        } else {
            LiquidBlobs(isDark)
        }

        // Content colors follow the glass, not the system theme: a dark
        // wallpaper gets a dark scheme with light text, a light one gets
        // frosted glass with dark text.
        val contentScheme = if (overWallpaper) LocalCrystalPalette.current.scheme(glassDark) else MaterialTheme.colorScheme
        MaterialTheme(colorScheme = contentScheme) {
            CompositionLocalProvider(LocalContentColor provides contentScheme.onBackground) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 20.dp),
                ) {
                    Header(state = state)

            Spacer(Modifier.height(12.dp))

            ConnectCard(
                isDark = glassDark,
                overWallpaper = overWallpaper,
                state = state,
                selected = profiles.firstOrNull { it.id == (state.profile?.id ?: selectedId) },
                onConnect = onConnect,
            )

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Профили",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${profiles.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 150.dp),
            ) {
                if (profiles.isEmpty()) {
                    item { EmptyProfiles(glassDark, onImport = onImport, onAdd = onAdd) }
                } else {
                    items(profiles, key = { it.id }) { p ->
                        ProfileCard(
                            profile = p,
                            selected = p.id == selectedId,
                            isDark = glassDark,
                            translucent = wallpaper != null,
                            onSelect = { onSelect(p) },
                            onEdit = { onEdit(p) },
                            onDelete = { onDelete(p) },
                        )
                    }
                }
            }
        }

        // Real frosted backdrop behind the tab bar: an exact blurred copy
        // of the wallpaper clipped to the bottom strip, so content sliding
        // under the bar dissolves into the frost instead of ghosting.
        // Drawn after the content, before the floating controls.
        TabBarBackdrop(
            wallpaper = wallpaper,
            isDark = glassDark,
            modifier = Modifier.matchParentSize(),
        )

        // FABs hover above the glass tab bar.
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(end = 20.dp, bottom = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ghost-accent FAB: translucent accent wash, accent icon.
            FloatingActionButton(
                onClick = onImport,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.border(1.dp, Glass.rim(glassDark), CircleShape),
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = "Import from file")
            }
            // Primary FAB carries the full accent.
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.border(1.dp, Glass.rim(glassDark), CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add profile")
            }
        }

        // Floating liquid-glass tab bar, iOS-style.
        GlassTabBar(
            isDark = glassDark,
            overWallpaper = overWallpaper,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, "Подключение"),
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 0,
            onSelect = { index -> if (index == 1) onSettings() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
            }
        }
    }
}

/** Soft color blobs slowly drifting behind the content, like light in liquid. */
@Composable
private fun LiquidBlobs(isDark: Boolean) {
    val blob = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.50f else 0.26f)
    val blob2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.38f else 0.20f)
    val blob3 = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.34f else 0.18f)

    val t = rememberInfiniteTransition(label = "blobs")
    val drift1 by t.animateFloat(
        -64f, 64f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift1",
    )
    val drift2 by t.animateFloat(
        48f, -48f,
        infiniteRepeatable(tween(11500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift2",
    )
    val drift3 by t.animateFloat(
        -40f, 40f,
        infiniteRepeatable(tween(13500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift3",
    )
    val breathe = rememberBreath(4200)

    Blob(blob, 460.dp, (-160 + drift1).dp, (-200 + drift1 * 0.6f).dp)
    Blob(blob2, 400.dp, (260 + drift2).dp, (160 - drift2 * 0.8f).dp)
    Blob(blob3, 340.dp, (-110 - drift3).dp, (620 + drift3).dp)
    Blob(
        blob.copy(alpha = blob.alpha * 0.75f),
        (380 + breathe * 50).dp,
        (70 + drift2 * 0.5f).dp,
        (960 - drift1 * 0.4f).dp,
    )
}

@Composable
private fun Blob(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color, Color.Transparent)))
            .then(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Modifier.blur(32.dp)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
private fun Header(state: VpnManager.UiState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(LocalCrystalPalette.current.gradient(false))),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", color = Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "CrystalN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.weight(1f))
        StatusPill(state)
    }
}

@Composable
private fun StatusPill(state: VpnManager.UiState) {
    val (label, color) = when (state.state) {
        VpnManager.State.OFF -> "Выкл" to com.crystaln.vpn.ui.theme.StatusOff
        VpnManager.State.STARTING -> "…" to com.crystaln.vpn.ui.theme.StatusConnecting
        VpnManager.State.CONNECTED -> "VPN" to com.crystaln.vpn.ui.theme.StatusConnected
        VpnManager.State.STOPPING -> "…" to com.crystaln.vpn.ui.theme.StatusConnecting
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
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

@Composable
private fun ConnectCard(
    isDark: Boolean,
    overWallpaper: Boolean,
    state: VpnManager.UiState,
    selected: Profile?,
    onConnect: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(32.dp), Glass.tint(isDark, overWallpaper), isDark)
            .padding(vertical = 14.dp, horizontal = 16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectButton(isDark = isDark, state = state, onClick = onConnect)

            Spacer(Modifier.height(10.dp))

            val (label, color) = when (state.state) {
                VpnManager.State.OFF -> "Отключено" to MaterialTheme.colorScheme.onSurface
                VpnManager.State.STARTING -> "Подключение…" to MaterialTheme.colorScheme.primary
                VpnManager.State.CONNECTED -> "Подключено" to com.crystaln.vpn.ui.theme.StatusConnected
                VpnManager.State.STOPPING -> "Отключение…" to MaterialTheme.colorScheme.primary
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )

            selected?.let { p ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(protocolColor(p.protocol)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${p.name} · ${p.protocol.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.state == VpnManager.State.CONNECTED && (state.rx > 0 || state.tx > 0)) {
                Spacer(Modifier.height(10.dp))
                TrafficRow(rx = state.rx, tx = state.tx)
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
}
@Composable
private fun TrafficRow(rx: Long, tx: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(humanBytes(rx), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(humanBytes(tx), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectButton(isDark: Boolean, state: VpnManager.UiState, onClick: () -> Unit) {
    val active = state.state != VpnManager.State.OFF
    val palette = LocalCrystalPalette.current
    val gradient = Brush.verticalGradient(palette.gradient(isDark))
    val busy = state.state == VpnManager.State.STARTING || state.state == VpnManager.State.STOPPING

    Box(contentAlignment = Alignment.Center) {
        if (active) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val scale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Restart),
                label = "scale",
            )
            val alpha by pulse.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Restart),
                label = "alpha",
            )
            Box(
                Modifier
                    .size(122.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(palette.accent2.copy(alpha = 0.85f)),
            )
        }
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(gradient)
                .liquidGlass(CircleShape, Color.White.copy(alpha = 0.28f), isDark)
                .liquidSheen(isDark)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(46.dp), strokeWidth = 4.dp)
            } else {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Connect",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    selected: Boolean,
    isDark: Boolean,
    translucent: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(
                RoundedCornerShape(20.dp),
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    Glass.tint(isDark, translucent)
                },
                isDark,
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(protocolColor(profile.protocol).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lan,
                    contentDescription = null,
                    tint = protocolColor(profile.protocol),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    profile.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (selected) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(2.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                BadgeChip(profile)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyProfiles(isDark: Boolean, onImport: () -> Unit, onAdd: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(20.dp), Glass.tint(isDark, false), isDark),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Lan,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(12.dp))
            Row {
                TextButton(onClick = onImport) { Text("Импорт") }
                TextButton(onClick = onAdd) { Text("Создать") }
            }
        }
    }
}

@Composable
fun BadgeChip(profile: Profile) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(protocolColor(profile.protocol).copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            profile.protocol.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = protocolColor(profile.protocol),
        )
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
