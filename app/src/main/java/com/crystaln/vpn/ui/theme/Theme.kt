package com.crystaln.vpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Crystal palette: deep indigo-violet with cyan accents.
private val Indigo = Color(0xFF4F6AF6)
private val IndigoLight = Color(0xFF8FA3FF)
private val Cyan = Color(0xFF38D6E8)
private val Emerald = Color(0xFF2BB673)
private val ErrorRed = Color(0xFFD93025)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E8FF),
    onPrimaryContainer = Color(0xFF0D1A66),
    secondary = Cyan,
    onSecondary = Color(0xFF00323A),
    secondaryContainer = Color(0xFFD6F5FA),
    onSecondaryContainer = Color(0xFF00242A),
    tertiary = Emerald,
    surface = Color(0xFFF8F9FE),
    onSurface = Color(0xFF191C26),
    surfaceVariant = Color(0xFFE4E7F3),
    onSurfaceVariant = Color(0xFF454B5E),
    error = ErrorRed,
    background = Color(0xFFF4F6FD),
    onBackground = Color(0xFF191C26),
    outline = Color(0xFFB9BFD4),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF0A1650),
    primaryContainer = Color(0xFF2C3E9E),
    onPrimaryContainer = Color(0xFFDEE3FF),
    secondary = Cyan,
    onSecondary = Color(0xFF00242A),
    secondaryContainer = Color(0xFF0B4A55),
    onSecondaryContainer = Color(0xFFB8ECF5),
    tertiary = Color(0xFF5CD69A),
    surface = Color(0xFF111322),
    onSurface = Color(0xFFE4E7F3),
    surfaceVariant = Color(0xFF232741),
    onSurfaceVariant = Color(0xFFAEB2CC),
    error = Color(0xFFFF6E5E),
    background = Color(0xFF0C0E1A),
    onBackground = Color(0xFFE4E7F3),
    outline = Color(0xFF4A5068),
)

@Composable
fun CrystalNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** Static crystal palette, used for glass that adapts to a dark wallpaper. */
fun crystalScheme(dark: Boolean): ColorScheme =
    if (dark) DarkColors else LightColors

/** Gradient used behind the connect action. */
val CrystalGradient = listOf(Color(0xFF4F6AF6), Color(0xFF7C4FF6))
val CrystalGradientDark = listOf(Color(0xFF2C3E9E), Color(0xFF5B2CA8))

/** Status accents shared across screens. */
val StatusOff = Color(0xFF9AA1BC)
val StatusConnecting = Color(0xFFE8A238)
val StatusConnected = Color(0xFF2BB673)
