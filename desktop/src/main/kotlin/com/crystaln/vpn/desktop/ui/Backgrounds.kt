package com.crystaln.vpn.desktop.ui

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.desktop.ui.glass.rememberBreath

/**
 * Soft color blobs slowly drifting behind the content, like light in
 * liquid. Ported 1:1 from the Android background (desktop has real
 * RenderEffect blur through skia).
 */
@Composable
fun LiquidBlobs(isDark: Boolean) {
    val blob = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.50f else 0.26f)
    val blob2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.38f else 0.20f)
    val blob3 = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.34f else 0.18f)

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

    Blob(blob, 460.dp, (-160 + drift1).dp, (-200 + drift1 * 0.6f).dp)
    Blob(blob2, 400.dp, (260 + drift2).dp, (160 - drift2 * 0.8f).dp)
    Blob(blob3, 340.dp, (-110 - drift3).dp, (620 + drift3).dp)
    Blob(
        blob.copy(alpha = blob.alpha * 0.75f),
        (380 + breathe * 50).dp,
        (70 + drift2 * 0.5f).dp,
        (960 - drift1 * 0.4f).dp,
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
            .blur(32.dp),
    )
}
