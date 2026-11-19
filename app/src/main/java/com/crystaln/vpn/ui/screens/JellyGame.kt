package com.crystaln.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.crystaln.vpn.R
import com.crystaln.vpn.ui.glass.CircleGlassButton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hidden mini game: a jelly CrystalN logo you can physically drag around —
 * flick it and it glides with inertia, bounces off the walls and wobbles
 * like jelly. Physics integrates per frame (withFrameNanos), the same way
 * the liquid background does. All transforms are read inside graphicsLayer,
 * so animation does not recompose the UI tree.
 */
@Composable
fun JellyGameOverlay(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                .systemBarsPadding(),
        ) {
            val jellySize = 96.dp
            val maxX = with(LocalDensity.current) { (maxWidth - jellySize).toPx() }
            val maxY = with(LocalDensity.current) { (maxHeight - jellySize).toPx() }

            // Physics state (px inside the padded box).
            var pos by remember { mutableStateOf(Offset(maxX / 2f, maxY / 3f)) }
            var vel by remember { mutableStateOf(Offset.Zero) }
            var dragging by remember { mutableStateOf(false) }
            // Jelly deformation driven by speed and wall hits.
            var stretch by remember { mutableStateOf(0f) }
            var angle by remember { mutableStateOf(0f) }
            var tilt by remember { mutableStateOf(0f) }
            var squish by remember { mutableStateOf(0f) }

            LaunchedEffect(maxX, maxY) {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9).coerceIn(1e-5, 0.05).toFloat()
                    last = now
                    if (!dragging) {
                        // Integrate: glide with friction, bounce off walls.
                        if (hypot(vel.x, vel.y) > 1f) {
                            var (x, y) = pos
                            x += vel.x * dt
                            y += vel.y * dt
                            if (x < 0f) { x = -x; vel = Offset(-vel.x * 0.6f, vel.y); squish = 0.35f }
                            if (x > maxX) { x = 2 * maxX - x; vel = Offset(-vel.x * 0.6f, vel.y); squish = 0.35f }
                            if (y < 0f) { y = -y; vel = Offset(vel.x, -vel.y * 0.6f); squish = 0.35f }
                            if (y > maxY) { y = 2 * maxY - y; vel = Offset(vel.x, -vel.y * 0.6f); squish = 0.35f }
                            pos = Offset(x.coerceIn(0f, maxX), y.coerceIn(0f, maxY))
                            vel = vel * 0.985f
                        } else {
                            vel = Offset.Zero
                        }
                    }
                    // Stretch along the motion vector; decay back when idle.
                    val speed = hypot(vel.x, vel.y)
                    val target = (speed / 2600f).coerceIn(0f, 0.40f)
                    stretch += (target - stretch) * 0.22f
                    if (speed > 1f) {
                        angle = Math.toDegrees(atan2(vel.y.toDouble(), vel.x.toDouble())).toFloat()
                    }
                    tilt += ((vel.x * 0.03f).coerceIn(-16f, 16f) - tilt) * 0.2f
                    squish *= 0.88f
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.jelly_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.jelly_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CircleGlassButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        isDark = true,
                        onClick = onDismiss,
                    )
                }
            }

            Box(
                Modifier
                    .size(jellySize)
                    .graphicsLayer {
                        // All reads inside the layer: no recomposition per frame.
                        translationX = pos.x
                        translationY = pos.y
                        val s = stretch + squish
                        val ca = abs(cos(Math.toRadians(angle.toDouble()))).toFloat()
                        val sa = abs(sin(Math.toRadians(angle.toDouble()))).toFloat()
                        // Stretch along the motion direction, squash across it.
                        scaleX = 1f + s * ca - s * 0.30f * sa
                        scaleY = 1f + s * sa - s * 0.30f * ca
                        rotationZ = tilt
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                vel = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Gesture deltas arrive per frame (~16 ms).
                                vel = Offset(
                                    dragAmount.x / 0.016f,
                                    dragAmount.y / 0.016f,
                                )
                                pos = Offset(
                                    (pos.x + dragAmount.x).coerceIn(0f, maxX),
                                    (pos.y + dragAmount.y).coerceIn(0f, maxY),
                                )
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        )
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF6C4FF6), Color(0xFF9D4FF6))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
    }
}
