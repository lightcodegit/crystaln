package com.crystaln.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import android.os.Build
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.glass.rememberBreath

/**
 * Soft color blobs slowly drifting behind the content, like light in
 * liquid. Used on every screen that has no custom wallpaper.
 *
 * Motion modes:
 * - "Жидкость" ([LocalLiquidBackground]): on API 33+ the blobs are
 *   rendered as a real fluid by [LiquidSurface] — metaballs that merge,
 *   neck and stretch; older devices use springy circles;
 * - "Гироскоп фон" ([LocalGyroBackground]): plain parallax from the
 *   device movement (see [rememberGyroDrift]).
 *
 * Every blob trails the raw drift with a different spring stiffness,
 * so the layers lag behind each other — that layered lag is what makes
 * the motion feel liquid instead of a rigid sheet.
 */
@Composable
fun LiquidBlobs(isDark: Boolean) {
    if (LocalLiquidBackground.current &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        metaballsSupported()
    ) {
        LiquidSurface(isDark)
        return
    }
    val blob = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.50f else 0.26f)
    val blob2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.38f else 0.20f)
    val blob3 = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.34f else 0.18f)

    val gyro = LocalGyroBackground.current
    val liquid = LocalLiquidBackground.current
    val liquidState = if (liquid) rememberLiquidDrift(true) else null
    val drift = when {
        liquidState != null -> liquidState.value.offset
        gyro -> rememberGyroDrift(true).value
        else -> Offset.Zero
    }

    // Layered lag: light blob follows fast, heavy blob drags behind.
    val d1 = rememberLagged(drift, 110f).value
    val d2 = rememberLagged(drift, 65f).value
    val d3 = rememberLagged(drift, 40f).value
    val d4 = rememberLagged(drift, 80f).value
    val gx1 = with(LocalDensity.current) { d1.x.toDp() }
    val gy1 = with(LocalDensity.current) { d1.y.toDp() }
    val gx2 = with(LocalDensity.current) { d2.x.toDp() }
    val gy2 = with(LocalDensity.current) { d2.y.toDp() }
    val gx3 = with(LocalDensity.current) { d3.x.toDp() }
    val gy3 = with(LocalDensity.current) { d3.y.toDp() }
    val gx4 = with(LocalDensity.current) { d4.x.toDp() }
    val gy4 = with(LocalDensity.current) { d4.y.toDp() }

    // Liquid surface tension: blobs swell with the slosh speed.
    val speed = liquidState?.value?.vel?.getDistance() ?: 0f
    val wob = (speed / 1400f).coerceIn(0f, 0.30f)

    val t = rememberInfiniteTransition(label = "blobs")
    val drift1 by t.animateFloat(
        -64f, 64f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift1",
    )
    val drift2 by t.animateFloat(
        48f, -48f,
        infiniteRepeatable(tween(11500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift2",
    )
    val drift3 by t.animateFloat(
        -40f, 40f,
        infiniteRepeatable(tween(13500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift3",
    )
    val breathe = rememberBreath(4200)

    Blob(blob, (460 * (1 + wob)).dp, (-160 + drift1).dp + gx1, (-200 + drift1 * 0.6f).dp + gy1)
    Blob(blob2, (400 * (1 + wob)).dp, (260 + drift2).dp - gx2, (160 - drift2 * 0.8f).dp - gy2)
    Blob(
        blob3,
        (340 * (1 + wob)).dp,
        (-110 - drift3).dp + gx3 * 0.7f,
        (620 + drift3).dp + gy3 * 0.7f,
    )
    Blob(
        blob.copy(alpha = blob.alpha * 0.75f),
        (380 * (1 + wob) + breathe * 50).dp,
        (70 + drift2 * 0.5f).dp - gx4 * 0.5f,
        (960 - drift1 * 0.4f).dp - gy4 * 0.5f,
    )
}

/** Springy trail of the raw drift: each layer lags differently. */
@Composable
internal fun rememberLagged(
    target: Offset,
    stiffness: Float,
): Animatable<Offset, AnimationVector2D> {
    val anim = remember(stiffness) { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(target, stiffness) {
        if (anim.value != target) {
            anim.animateTo(target, spring(stiffness = stiffness, dampingRatio = 0.85f))
        }
    }
    return anim
}

@Composable
fun Blob(color: Color, size: Dp, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(color, Color.Transparent)))
            .then(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Modifier.blur(32.dp)
                } else {
                    Modifier
                },
            ),
    )
}
