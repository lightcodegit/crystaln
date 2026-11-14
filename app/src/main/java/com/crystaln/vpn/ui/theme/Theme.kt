package com.crystaln.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

@Composable
fun CrystalNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    paletteId: String = IndigoPalette.id,
    content: @Composable () -> Unit,
) {
    val palette = paletteById(paletteId)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> palette.scheme(darkTheme)
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
