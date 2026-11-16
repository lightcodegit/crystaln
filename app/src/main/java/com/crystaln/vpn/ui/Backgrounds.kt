package com.crystaln.vpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.glass.rememberBreath

/**
 * Soft color blobs slowly drifting behind the content, like light in
 * liquid. Used on every screen that has no custom wallpaper.
 *
 * When the "Гироскоп фон" setting is on, the blobs additionally shift with
 * the phone's movement (parallax) — see [rememberGyroDrift].
 */
@Composable
fun LiquidBlobs(isDark: Boolean) {
    val blob = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.50f else 0.26f)
    val blob2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.38f else 0.20f)
    val blob3 = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.34f else 0.18f)

    val gyro = LocalGyroBackground.current
    val drift = if (gyro) rememberGyroDrift(true).value else Offset.Zero
    val gx = with(LocalDensity.current) { drift.x.toDp() }
    val gy = with(LocalDensity.current) { drift.y.toDp() }

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

    Blob(blob, 460.dp, (-160 + drift1).dp + gx, (-200 + drift1 * 0.6f).dp + gy)
    Blob(blob2, 400.dp, (260 + drift2).dp - gx, (160 - drift2 * 0.8f).dp - gy)
    Blob(blob3, 340.dp, (-110 - drift3).dp + gx * 0.7f, (620 + drift3).dp + gy * 0.7f)
    Blob(
        blob.copy(alpha = blob.alpha * 0.75f),
        (380 + breathe * 50).dp,
        (70 + drift2 * 0.5f).dp - gx * 0.5f,
        (960 - drift1 * 0.4f).dp - gy * 0.5f,
    )
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
