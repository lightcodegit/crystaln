package com.crystaln.vpn.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.BuildConfig
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDark: Boolean,
    themeMode: SettingsStore.ThemeMode,
    onThemeChange: (SettingsStore.ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    wallpaperSet: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { pad ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            SettingsCard(isDark = isDark, icon = Icons.Default.Palette, title = "Оформление") {
                Text("Тема", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == SettingsStore.ThemeMode.SYSTEM,
                        onClick = { onThemeChange(SettingsStore.ThemeMode.SYSTEM) },
                        label = { Text("Системная") },
                    )
                    FilterChip(
                        selected = themeMode == SettingsStore.ThemeMode.LIGHT,
                        onClick = { onThemeChange(SettingsStore.ThemeMode.LIGHT) },
                        label = { Text("Светлая") },
                    )
                    FilterChip(
                        selected = themeMode == SettingsStore.ThemeMode.DARK,
                        onClick = { onThemeChange(SettingsStore.ThemeMode.DARK) },
                        label = { Text("Тёмная") },
                    )
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    title = "Цвета системы (Material You)",
                    subtitle = "Палитра из обоев. Android 12+",
                    checked = dynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onChange = onDynamicColorChange,
                )
                Spacer(Modifier.height(14.dp))
                Text("Свои обои", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (wallpaperSet) "Обои установлены" else "Обои не выбраны",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickWallpaper,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Выбрать")
                    }
                    Button(
                        onClick = onClearWallpaper,
                        enabled = wallpaperSet,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сбросить")
                    }
                }
            }

            SettingsCard(isDark = isDark, icon = Icons.Default.Bolt, title = "Подключение") {
                SwitchRow(
                    title = "Автоподключение при запуске",
                    subtitle = "Соединять выбранный профиль при открытии приложения",
                    checked = autoConnect,
                    onChange = onAutoConnectChange,
                )
            }

            SettingsCard(isDark = isDark, icon = Icons.Default.FileOpen, title = "Профили") {
                Text(
                    "Импорт: файл конфигурации (.conf / .txt / .json), ссылки vless:// trojan:// ss:// vmess:// socks5:// или wg-quick конфиг.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Импорт")
                    }
                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Экспорт")
                    }
                }
            }

            SettingsCard(isDark = isDark, icon = Icons.Default.Info, title = "О приложении") {
                Text(
                    "CrystalN ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ядра: Xray (VMess/VLESS/Trojan/Shadowsocks/SOCKS/HTTP), " +
                        "WireGuard и AmneziaWG (amneziawg-go, junk-параметры Jc/Jmin/Jmax/S1/S2/H1–H4).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
        }

        // Floating liquid-glass tab bar, iOS-style.
        GlassTabBar(
            isDark = isDark,
            tabs = listOf(
                GlassTab(Icons.Default.Bolt, "Подключение"),
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 1,
            onSelect = { index -> if (index == 0) onBack() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SettingsCard(
    isDark: Boolean,
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(24.dp), Glass.tint(isDark, false), isDark),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
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
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
