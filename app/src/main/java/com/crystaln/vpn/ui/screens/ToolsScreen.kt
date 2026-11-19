package com.crystaln.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.R
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassPanel
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.rememberWallpaper
import com.crystaln.vpn.ui.rememberWallpaper
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Tools tab: split tunneling, kill-switch, diagnostics and URL-test. */
@Composable
fun ToolsScreen(
    isDark: Boolean,
    wallpaperPath: String? = null,
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    onSplitTunnel: () -> Unit,
    onDiagnostics: () -> Unit,
    onUrlTest: () -> Unit,
    onBack: () -> Unit = {},
    onProfiles: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val wallpaper = rememberWallpaper(wallpaperPath)
    val overWallpaper = wallpaper != null
    val glassDark = wallpaper?.isDark ?: isDark

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val hazeState = rememberHazeState()

        Box(Modifier.matchParentSize().hazeSource(hazeState)) {
            if (wallpaper != null) {
                WallpaperLayer(wallpaper.bitmap, wallpaper.isDark, themeDark = isDark)
            } else {
                LiquidBlobs(isDark)
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        stringResource(R.string.tab_tools),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(16.dp))

                    GlassPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        ToolRow(Icons.Default.Apps, stringResource(R.string.split_tunnel_title), "Выбор приложений для VPN") { onSplitTunnel() }
                        ToolRow(
                            Icons.Default.GppGood,
                            stringResource(R.string.kill_switch_title),
                            stringResource(R.string.kill_switch_sub),
                            trailing = {
                                Switch(checked = killSwitch, onCheckedChange = onKillSwitchChange)
                            },
                        )
                        ToolRow(Icons.Default.Bolt, stringResource(R.string.diag_title), "Проверка конфига и доступности") { onDiagnostics() }
                        ToolRow(Icons.Default.Speed, stringResource(R.string.url_test_title), "Подключить самый быстрый сервер") { onUrlTest() }
                    }
                    Spacer(Modifier.height(170.dp))
                }
            }
        }

        GlassTabBar(
            hazeState = hazeState,
            isDark = glassDark,
            overWallpaper = overWallpaper,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, stringResource(R.string.tab_connect)),
                GlassTab(Icons.Default.Lan, stringResource(R.string.tab_profiles)),
                GlassTab(Icons.Default.Build, stringResource(R.string.tab_tools)),
                GlassTab(Icons.Default.Settings, stringResource(R.string.tab_settings)),
            ),
            selected = 2,
            onSelect = { index ->
                when (index) {
                    0 -> onBack()
                    1 -> onProfiles()
                    3 -> onSettings()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}
