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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.GlassDivider
import com.crystaln.vpn.ui.glass.GlassSegmented
import com.crystaln.vpn.ui.glass.GlassTab
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.PillButton
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
    autoConnect: Boolean,
    onAutoConnectChange: (Boolean) -> Unit,
    wallpaperSet: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    val wallpaper = rememberWallpaper(SettingsStore.wallpaper(LocalContext.current))
    val glassDark = wallpaper?.isDark ?: isDark
    val overWallpaper = wallpaper != null

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
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
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
                            overWallpaper = overWallpaper,
                        )
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        SwitchRow(
                            title = "Цвета системы",
                            subtitle = "Material You — палитра из обоев. Android 12+",
                            checked = dynamicColor,
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onChange = onDynamicColorChange,
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
                        GlassDivider(Modifier.padding(vertical = 14.dp))
                        Text(
                            "Свои обои",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (wallpaperSet) "Обои установлены — размытая копия служит фоном стекла" else "Обои не выбраны — фон собирается из палитры",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton(
                                text = "Выбрать",
                                icon = Icons.Default.Image,
                                accent = true,
                                isDark = glassDark,
                                onClick = onPickWallpaper,
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(
                                text = "Сбросить",
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
                    SectionLabel("Подключение")
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        SwitchRow(
                            title = "Автоподключение",
                            subtitle = "Соединять выбранный профиль при запуске приложения",
                            checked = autoConnect,
                            onChange = onAutoConnectChange,
                        )
                    }

                    // ── Data ───────────────────────────────────────────
                    SectionLabel("Профили и данные")
                    SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                        Text(
                            "Импорт: файл конфигурации (.conf / .txt / .json), ссылки " +
                                "vless:// trojan:// ss:// vmess:// socks5:// или wg-quick конфиг.",
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
                                overWallpaper = overWallpaper,
                                onClick = onExport,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // ── About ──────────────────────────────────────────
                    SectionLabel("О приложении")
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
                            "Ядра: Xray (VMess/VLESS/Trojan/Shadowsocks/SOCKS/HTTP), " +
                                "WireGuard и AmneziaWG (amneziawg-go, junk-параметры " +
                                "Jc/Jmin/Jmax/S1/S2/H1–H4).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Clearance so the last rows sit above the tab bar;
                    // content under the bar is genuinely frosted by Haze.
                    Spacer(Modifier.height(170.dp))
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
                GlassTab(Icons.Default.PowerSettingsNew, "Подключение"),
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

/** A rounded glass panel holding the rows of one settings section. */
@Composable
private fun SettingsPanel(
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
        "Выбрано: ${selected.name}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
