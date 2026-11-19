package com.crystaln.vpn.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Desktop port of the Android theme: no dynamic color (that is Material You). */
@Composable
fun CrystalNTheme(
    darkTheme: Boolean,
    paletteId: String = IndigoPalette.id,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = paletteById(paletteId)
    CompositionLocalProvider(LocalCrystalPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.scheme(darkTheme, amoledMode),
            content = content,
        )
    }
}

/** Status accents shared across screens (theme-independent). */
val StatusOff = Color(0xFF9AA1BC)
val StatusConnecting = Color(0xFFE8A238)
val StatusConnected = Color(0xFF2BB673)
