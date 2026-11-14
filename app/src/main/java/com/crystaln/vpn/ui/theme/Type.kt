package com.crystaln.vpn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.crystaln.vpn.R

/**
 * Manrope — a geometric sans in the spirit of SF Pro: round, wide,
 * with clean numerals. Both cyrillic and latin subsets are bundled,
 * Compose picks the right file per glyph.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_400_cyrillic, FontWeight.Normal),
    Font(R.font.manrope_400_latin, FontWeight.Normal),
    Font(R.font.manrope_500_cyrillic, FontWeight.Medium),
    Font(R.font.manrope_500_latin, FontWeight.Medium),
    Font(R.font.manrope_600_cyrillic, FontWeight.SemiBold),
    Font(R.font.manrope_600_latin, FontWeight.SemiBold),
    Font(R.font.manrope_700_cyrillic, FontWeight.Bold),
    Font(R.font.manrope_700_latin, FontWeight.Bold),
    Font(R.font.manrope_800_cyrillic, FontWeight.ExtraBold),
    Font(R.font.manrope_800_latin, FontWeight.ExtraBold),
)

private val Base = Typography()

/** App typography: Manrope everywhere, slightly tighter display sizes. */
val CrystalTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Manrope, letterSpacing = (-0.5).sp),
    displayMedium = Base.displayMedium.copy(fontFamily = Manrope, letterSpacing = (-0.4).sp),
    displaySmall = Base.displaySmall.copy(fontFamily = Manrope, letterSpacing = (-0.3).sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Manrope, letterSpacing = (-0.3).sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Manrope, letterSpacing = (-0.2).sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Manrope, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Manrope),
    titleMedium = Base.titleMedium.copy(fontFamily = Manrope),
    titleSmall = Base.titleSmall.copy(fontFamily = Manrope),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = Base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = Base.labelLarge.copy(fontFamily = Manrope),
    labelMedium = Base.labelMedium.copy(fontFamily = Manrope),
    labelSmall = Base.labelSmall.copy(fontFamily = Manrope),
)

/** Handy shorthand for the boldest display text (headers, big numbers). */
val DisplayHeavy = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    letterSpacing = (-0.2).sp,
)
