package com.crystaln.vpn.ui.glass

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Liquid Glass, the Apple-style recipe:
 *
 *  1. The surface is nearly transparent — the frosted look comes from a
 *     heavily blurred backdrop behind it, not from a white fill.
 *  2. Light bends at the edges: an inner "lens" ring catches light around
 *     the border.
 *  3. Specular highlights sit on the top-left (main light) with a weaker
 *     counter-glint bottom-right, plus a soft reflection on the top edge.
 *  4. A thin bright rim traces the whole shape.
 */
object Glass {

    /** Base translucent fill. Deliberately light — blur does the frosting. */
    fun tint(isDark: Boolean, overWallpaper: Boolean): Color =
        if (isDark) {
            Color(0xFF10131F).copy(alpha = if (overWallpaper) 0.48f else 0.55f)
        } else {
            Color.White.copy(alpha = if (overWallpaper) 0.50f else 0.55f)
        }

    /** Slightly stronger fill for small elements (pills, chips). */
    fun tintSoft(isDark: Boolean, overWallpaper: Boolean): Color =
        if (isDark) {
            Color(0xFF161A2B).copy(alpha = if (overWallpaper) 0.55f else 0.65f)
        } else {
            Color.White.copy(alpha = if (overWallpaper) 0.58f else 0.65f)
        }

    /**
     * Fill for floating controls (tab bar, FAB-like pills) that hover over
     * sharp app content — not the blurred backdrop. Content behind them is
     * in focus, so the fill must be dense enough to keep text readable.
     */
    fun tintFloating(isDark: Boolean, overWallpaper: Boolean): Color =
        if (isDark) {
            Color(0xFF12151F).copy(alpha = if (overWallpaper) 0.72f else 0.84f)
        } else {
            Color.White.copy(alpha = if (overWallpaper) 0.76f else 0.88f)
        }

    /** Light-bending rim: bright at top-left, a mid glint at bottom-right. */
    fun rim(dark: Boolean): Brush = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = if (dark) 0.30f else 0.50f),
        ),
    )

    fun border(dark: Boolean): BorderStroke =
        BorderStroke(1.dp, rim(dark))
}

/**
 * Draws the full glass stack UNDER the content for [shape]:
 * tint -> specular streak (top-left) -> counter glint (bottom-right) ->
 * top edge reflection -> inner lens ring -> content.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Color,
    isDark: Boolean,
    flat: Boolean = false,
): Modifier = this
    .clip(shape)
    .drawWithContent {
        // 1. Base tint with a vertical falloff (uniform for floating bars).
        if (flat) {
            drawRect(fill)
        } else {
            drawRect(
                Brush.verticalGradient(
                    listOf(fill.copy(alpha = fill.alpha * 0.65f), fill),
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }

        // 2. Specular streak from the top-left — the main light source.
        drawRect(
            Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.20f else 0.38f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.10f, size.height * -0.05f),
                radius = size.width * 1.15f,
            ),
        )

        // 3. Weaker counter-glint bottom-right — glass bends light both ways.
        drawRect(
            Brush.radialGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.09f else 0.16f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 1.05f, size.height * 1.08f),
                radius = size.width * 0.85f,
            ),
        )

        // 4. Reflection hugging the top edge, fading out fast.
        drawRect(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.13f else 0.28f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.35f,
            ),
            size = Size(size.width, size.height * 0.35f),
        )

        // 5. Inner lens ring: a soft, wide stroke + a thin bright one, both
        //    centered on the outline. The clip keeps only the inner halves,
        //    which reads as light refracting through the edge.
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.30f else 0.60f),
                    Color.Transparent,
                    Color.White.copy(alpha = if (isDark) 0.20f else 0.40f),
                ),
                startY = 0f,
                endY = size.height,
            ),
            style = Stroke(width = 8.dp.toPx()),
        )
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.45f else 0.85f),
                    Color.Transparent,
                    Color.White.copy(alpha = if (isDark) 0.30f else 0.55f),
                ),
                startY = 0f,
                endY = size.height,
            ),
            style = Stroke(width = 2.dp.toPx()),
        )

        drawContent()
    }
    .border(1.dp, Glass.rim(isDark), shape)

/**
 * Animated sheen sweeping across a glass surface, like light refracting
 * through liquid. Draw it as the last layer inside a clipped node.
 */
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
