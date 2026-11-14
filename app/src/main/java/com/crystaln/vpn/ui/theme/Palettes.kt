package com.crystaln.vpn.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A named color palette: light + dark Material schemes plus the accent
 * gradient used behind the connect action. Neutrals are derived from the
 * accent hue so surfaces never look dead-gray.
 */
data class CrystalPalette(
    val id: String,
    val name: String,
    /** Color used for the swatch dot in settings. */
    val swatch: Color,
    /** Main accent, light scheme. */
    val accent: Color,
    /** Lighter accent for the dark scheme (better contrast on dark). */
    val accentDark: Color,
    /** Supporting accent (secondary). */
    val accent2: Color,
) {
    private val hueDark = lerp(Color(0xFF0C0E1A), accent, 0.055f)
    private val surfaceDark = lerp(Color(0xFF111322), accent, 0.06f)
    private val variantDark = lerp(Color(0xFF232741), accent, 0.10f)

    fun scheme(dark: Boolean): ColorScheme =
        if (dark) {
            darkColorScheme(
                primary = accentDark,
                onPrimary = lerp(Color(0xFF060810), accent, 0.72f),
                primaryContainer = lerp(Color(0xFF1B1F33), accent, 0.45f),
                onPrimaryContainer = lerp(Color(0xFFE6EBFF), accent, 0.30f),
                secondary = accent2,
                onSecondary = lerp(Color(0xFF060810), accent2, 0.72f),
                secondaryContainer = lerp(Color(0xFF1B1F33), accent2, 0.48f),
                onSecondaryContainer = lerp(Color(0xFFE6EBFF), accent2, 0.35f),
                tertiary = lerp(accent2, accent, 0.45f),
                surface = surfaceDark,
                onSurface = Color(0xFFE7EAF6),
                surfaceVariant = variantDark,
                onSurfaceVariant = lerp(Color(0xFFB0B4CE), accent, 0.12f),
                error = Color(0xFFFF6E5E),
                background = hueDark,
                onBackground = Color(0xFFE7EAF6),
                outline = lerp(Color(0xFF4A5068), accent, 0.22f),
            )
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = lerp(Color.White, accent, 0.20f),
                onPrimaryContainer = lerp(Color(0xFF0A1026), accent, 0.72f),
                secondary = accent2,
                onSecondary = lerp(Color(0xFF00131A), accent2, 0.72f),
                secondaryContainer = lerp(Color.White, accent2, 0.24f),
                onSecondaryContainer = lerp(Color(0xFF00131A), accent2, 0.75f),
                tertiary = lerp(accent2, accent, 0.45f),
                surface = lerp(Color(0xFFF8F9FE), accent, 0.035f),
                onSurface = Color(0xFF191C26),
                surfaceVariant = lerp(Color(0xFFE4E7F3), accent, 0.07f),
                onSurfaceVariant = lerp(Color(0xFF454B5E), accent, 0.10f),
                error = Color(0xFFD93025),
                background = lerp(Color(0xFFF4F6FD), accent, 0.045f),
                onBackground = Color(0xFF191C26),
                outline = lerp(Color(0xFFB9BFD4), accent, 0.25f),
            )
        }

    /** Gradient behind the connect action / primary FAB. */
    fun gradient(dark: Boolean): List<Color> =
        if (dark) {
            listOf(
                lerp(Color(0xFF171A30), accent, 0.55f),
                lerp(Color(0xFF171A30), accent2, 0.55f),
            )
        } else {
            listOf(accent, lerp(accent, accent2, 0.65f))
        }
}

/** Accent-tinted dark glass so panels never read as dead gray. */
val DarkGlassTint = Color(0xFF0B1122)

val LocalCrystalPalette = staticCompositionLocalOf { IndigoPalette }

val IndigoPalette = CrystalPalette(
    id = "indigo",
    name = "Индиго",
    swatch = Color(0xFF4F6AF6),
    accent = Color(0xFF4F6AF6),
    accentDark = Color(0xFF93A7FF),
    accent2 = Color(0xFF38D6E8),
)

val OceanPalette = CrystalPalette(
    id = "ocean",
    name = "Океан",
    swatch = Color(0xFF1E88E5),
    accent = Color(0xFF1E88E5),
    accentDark = Color(0xFF8AB8FF),
    accent2 = Color(0xFF26C6DA),
)

val AmethystPalette = CrystalPalette(
    id = "amethyst",
    name = "Аметист",
    swatch = Color(0xFF9C4DE0),
    accent = Color(0xFF9C4DE0),
    accentDark = Color(0xFFCFA2FF),
    accent2 = Color(0xFFFF6EC7),
)

val EmeraldPalette = CrystalPalette(
    id = "emerald",
    name = "Изумруд",
    swatch = Color(0xFF10B981),
    accent = Color(0xFF0FB57F),
    accentDark = Color(0xFF6FE7BD),
    accent2 = Color(0xFF22C8D6),
)

val SunsetPalette = CrystalPalette(
    id = "sunset",
    name = "Закат",
    swatch = Color(0xFFF4623A),
    accent = Color(0xFFF4623A),
    accentDark = Color(0xFFFFA489),
    accent2 = Color(0xFFFFB845),
)

val RosePalette = CrystalPalette(
    id = "rose",
    name = "Роза",
    swatch = Color(0xFFE8457F),
    accent = Color(0xFFE8457F),
    accentDark = Color(0xFFFF9CC0),
    accent2 = Color(0xFFA97BFF),
)

val CrystalPalettes: List<CrystalPalette> = listOf(
    IndigoPalette,
    OceanPalette,
    AmethystPalette,
    EmeraldPalette,
    SunsetPalette,
    RosePalette,
)

fun paletteById(id: String): CrystalPalette =
    CrystalPalettes.firstOrNull { it.id == id } ?: IndigoPalette
