package com.crystaln.vpn.ui.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass: frosted translucent surfaces with a specular rim, a soft
 * reflection on the top edge and an animated sheen — the glass look, tuned
 * per light/dark and per background type.
 */
object Glass {

    /** Base translucent fill for a glass surface. */
    fun tint(isDark: Boolean, overWallpaper: Boolean): Color =
        if (isDark) {
            Color(0xFF151A33).copy(alpha = if (overWallpaper) 0.60f else 0.82f)
        } else {
            Color.White.copy(alpha = if (overWallpaper) 0.60f else 0.80f)
        }

    /** Slightly stronger fill for small elements (pills, chips). */
    fun tintSoft(isDark: Boolean, overWallpaper: Boolean): Color =
        if (isDark) {
            Color(0xFF1B2040).copy(alpha = if (overWallpaper) 0.50f else 0.72f)
        } else {
            Color.White.copy(alpha = if (overWallpaper) 0.52f else 0.70f)
        }

    /** Light-bending rim: bright at top-left, fading along the edge. */
    fun rim(dark: Boolean): Brush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.30f else 0.90f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = if (dark) 0.16f else 0.40f),
        ),
    )

    fun border(dark: Boolean): BorderStroke =
        BorderStroke(1.2.dp, rim(dark))

    /** Soft reflection hugging the top edge of a glass surface. */
    fun topReflection(isDark: Boolean): Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.10f else 0.26f),
            Color.Transparent,
        ),
    )
}

/** Draws a glass fill + specular rim + top reflection for [shape]. */
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Color,
    isDark: Boolean,
): Modifier = this
    .clip(shape)
    .drawWithContent {
        // Glass fill and reflection go UNDER the content. Gradients get
        // explicit bounds — the default infinite span would never fade out
        // and leave a hard edge at the reflection's end.
        drawRect(
            Brush.verticalGradient(
                listOf(fill.copy(alpha = fill.alpha * 0.80f), fill),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.10f else 0.24f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.40f,
            ),
            size = Size(size.width, size.height * 0.40f),
        )
        drawContent()
    }
    .border(1.2.dp, Glass.rim(isDark), shape)

/** Animated sheen sweeping across a glass surface, like light refracting
 * through liquid. Draw it as the last layer inside a clipped node. */
@Composable
fun Modifier.liquidSheen(
    isDark: Boolean,
    periodMs: Int = 5200,
): Modifier {
    val transition = rememberInfiniteTransition(label = "sheen")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMs, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "t",
    )
    return drawWithContent {
        drawContent()
        val band = size.width * 0.55f
        val x = t * (size.width + band) - band
        drawRect(
            brush = Brush.linearGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = if (isDark) 0.10f else 0.22f),
                    Color.Transparent,
                ),
                start = Offset(x, 0f),
                end = Offset(x + band, size.height),
            ),
        )
    }
}

/** Gentle one-off breathing motion for glow halos. */
@Composable
fun rememberBreath(periodMs: Int = 3000): Float {
    val transition = rememberInfiniteTransition(label = "breath")
    val v by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMs, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "v",
    )
    return v
}
