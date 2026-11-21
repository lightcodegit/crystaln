package com.crystaln.vpn.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.rememberWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Split tunneling: choose apps that bypass or exclusively use the VPN. */
@Composable
fun SplitTunnelScreen(
    isDark: Boolean,
    mode: Int,
    onModeChange: (Int) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    wallpaperPath: String? = null,
) {
    val ctx = LocalContext.current
    val wallpaper = rememberWallpaper(wallpaperPath)
    val overWallpaper = wallpaper != null

    // Reactive mirror of SettingsStore.splitApps: SharedPreferences is not
    // observable, so without a state here the checkboxes would never update
    // after a tap (the row clickable and the checkbox used to double-fire).
    var selected by remember { mutableStateOf(SettingsStore.splitApps(ctx)) }

    // The installed-apps list is slow to enumerate; load it off the main
    // thread so opening the screen doesn't freeze the UI.
    var apps by remember { mutableStateOf<List<ApplicationInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .sortedBy { ctx.packageManager.getApplicationLabel(it).toString() }
        }
    }

    val toggleApp = { pkg: String ->
        val next = selected.toMutableSet()
        if (pkg in next) next.remove(pkg) else next.add(pkg)
        val checked = next.contains(pkg)
        selected = next.toSet()
        onToggleApp(pkg, checked)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Wallpaper behind the glass like the other screens with a custom
        // background — split tunneling had no background at all.
        if (wallpaper != null) {
            WallpaperLayer(wallpaper.bitmap, wallpaper.isDark, themeDark = isDark)
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleGlassButton(
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    isDark = isDark,
                    onClick = onBack,
                    size = 40.dp,
                    overWallpaper = overWallpaper,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Split tunneling",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Выкл", 1 to "Исключить", 2 to "Только выбранные").forEach { (m, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = mode == m,
                        onClick = { onModeChange(m) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    1 -> "Выбранные приложения пойдут мимо VPN"
                    2 -> "Только выбранные приложения пойдут через VPN"
                    else -> "Весь трафик идёт через VPN"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { ai ->
                    val label = ctx.packageManager.getApplicationLabel(ai).toString()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggleApp(ai.packageName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Tapping the row toggles; the checkbox is not a
                        // second tap target — it used to double-fire with
                        // the row's clickable.
                        Checkbox(
                            checked = ai.packageName in selected,
                            onCheckedChange = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
