package com.crystaln.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun CrystalNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    paletteId: String = IndigoPalette.id,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteById(paletteId)
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> palette.scheme(darkTheme)
    }
    if (darkTheme && amoledMode) {
        // True black: OLED pixels are off, saving battery and removing the
        // gray wash in dark rooms. Accents stay untouched for contrast.
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0C0C12),
            surfaceDim = Color(0xFF08080C),
            surfaceBright = Color(0xFF16161E),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF09090E),
            surfaceContainer = Color(0xFF0C0C12),
            surfaceContainerHigh = Color(0xFF111118),
            surfaceContainerHighest = Color(0xFF171720),
        )
    }
    CompositionLocalProvider(LocalCrystalPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/** Status accents shared across screens (theme-independent). */
val StatusOff = androidx.compose.ui.graphics.Color(0xFF9AA1BC)
val StatusConnecting = androidx.compose.ui.graphics.Color(0xFFE8A238)
val StatusConnected = androidx.compose.ui.graphics.Color(0xFF2BB673)
