package com.crystaln.vpn.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * Provided by MainActivity: the "Гироскоп фон" setting. LiquidBlobs reads it
 * to decide whether blobs should follow the phone's movement, so every
 * screen picks the value up without parameter plumbing.
 */
val LocalGyroBackground = staticCompositionLocalOf { false }

/** Max blob shift from the device movement, in pixels. */
private const val GYRO_MAX_PX = 90f

/** Rotation-rate → pixels gain. */
private const val GYRO_GAIN = 260f

/** Exponential decay per event toward center (0.93 ≈ calm return). */
private const val GYRO_DECAY = 0.93f

/**
 * Integrates gyroscope rotation rates into a smooth parallax offset.
 * Movement of the phone pushes the offset; without movement it slowly
 * drifts back to center. Falls back to (0,0) when the device has no
 * gyroscope or the feature is off.
 */
@Composable
fun rememberGyroDrift(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val drift = remember { mutableStateOf(Offset.Zero) }

    if (enabled) {
        DisposableEffect(enabled) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            var lastTimestamp = 0L
            var dx = 0f
            var dy = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val dt = if (lastTimestamp == 0L) {
                        0f
                    } else {
                        ((event.timestamp - lastTimestamp) / 1_000_000_000f)
                            .coerceIn(0.001f, 0.2f)
                    }
                    lastTimestamp = event.timestamp
                    // values[0] = pitch rate (tilt up/down), values[1] = roll
                    // rate (tilt left/right): blobs move opposite to rotation,
                    // like a layer floating above the content.
                    dx = (dx - event.values[1] * dt * GYRO_GAIN) * GYRO_DECAY
                    dy = (dy - event.values[0] * dt * GYRO_GAIN) * GYRO_DECAY
                    dx = dx.coerceIn(-GYRO_MAX_PX, GYRO_MAX_PX)
                    dy = dy.coerceIn(-GYRO_MAX_PX, GYRO_MAX_PX)
                    // Round to half-pixels: fewer recompositions, same feel.
                    val rx = (dx * 2).toInt() / 2f
                    val ry = (dy * 2).toInt() / 2f
                    if (rx != drift.value.x || ry != drift.value.y) {
                        drift.value = Offset(rx, ry)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = sensor != null &&
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                if (registered) sm.unregisterListener(listener)
                drift.value = Offset.Zero
            }
        }
    }

    return drift
}
