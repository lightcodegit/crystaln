package com.crystaln.vpn.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.isActive
import kotlin.math.exp

/** Provided by MainActivity: the "Гироскоп фон" setting. */
val LocalGyroBackground = staticCompositionLocalOf { false }

/** Provided by MainActivity: the "Жидкость" setting (liquid physics mode). */
val LocalLiquidBackground = staticCompositionLocalOf { false }

// Plain gyro-parallax tuning ("Гироскоп фон").
/** Max blob shift from the device movement, in pixels. */
private const val GYRO_MAX_PX = 140f

/** Rotation-rate → pixels gain. */
private const val GYRO_GAIN = 380f

/** Exponential decay per event toward center (0.93 ≈ calm return). */
private const val GYRO_DECAY = 0.93f

// Liquid physics tuning ("Жидкость") — px and seconds.
/** How far the liquid pools at a full 90° tilt. */
private const val LIQUID_TILT_MAX_PX = 130f

/** Damped-spring pull toward the tilt target (1/s²). Underdamped on
 *  purpose: the surface sloshes around the equilibrium, like real liquid. */
private const val LIQUID_STIFFNESS = 30f

/** Damped spring velocity decay (1/s). Low = long viscous sloshing. */
private const val LIQUID_DAMPING = 1.35f

/** Gyro rotation → slosh velocity kick (px/s per rad/s). */
private const val LIQUID_KICK = 280f

/** Drift with velocity — velocity drives the blob wobble. */
data class Drift(val offset: Offset, val vel: Offset)

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

/**
 * Liquid physics for the "Жидкость" mode. The color blobs are treated as
 * a pool of liquid laid over the screen:
 * - a **tilt** (accelerometer gravity) makes the liquid pool toward the
 *   physically lower side of the screen — a damped spring chases that
 *   target, so the surface sloshes and settles like real liquid;
 * - a **shake or flick** (gyroscope rotation rate) kicks the surface into
 *   motion, opposite to the rotation direction (inertia).
 *
 * The simulation runs once per frame (withFrameNanos), so the motion is
 * smooth at display rate instead of jittering at sensor rate.
 */
@Composable
fun rememberLiquidDrift(enabled: Boolean): State<Drift> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Drift(Offset.Zero, Offset.Zero)) }
    if (!enabled) return state

    val physics = remember { LiquidPhysics() }
    DisposableEffect(enabled) {
        physics.attach(context)
        onDispose {
            physics.detach()
            state.value = Drift(Offset.Zero, Offset.Zero)
        }
    }
    LaunchedEffect(enabled) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                if (last > 0) {
                    val dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    physics.step(dt, state)
                }
                last = now
            }
        }
    }
    return state
}

/** Frame-stepped liquid simulation; thread-safe sensor input. */
private class LiquidPhysics {
    // Gravity (accelerometer), device coords: flat screen-up ≈ (0, 9.8).
    @Volatile private var g0 = 0f
    @Volatile private var g1 = 9.8f

    // Latest rotation rates (rad/s): values[0] pitch, values[1] roll.
    @Volatile private var rx = 0f
    @Volatile private var ry = 0f

    // Simulation state: surface position and velocity, px / px·s.
    private var px = 0f
    private var py = 0f
    private var vx = 0f
    private var vy = 0f

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            g0 = event.values[0]
            g1 = event.values[1]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            rx = event.values[0]
            ry = event.values[1]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var manager: SensorManager? = null

    fun attach(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager = sm
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun detach() {
        manager?.let {
            it.unregisterListener(accelListener)
            it.unregisterListener(gyroListener)
        }
        manager = null
    }

    fun step(dt: Float, out: MutableState<Drift>) {
        // Target: liquid pools toward the physically lower side of the
        // screen. Accelerometer reads proper acceleration = -gravity:
        // right edge down → g0 < 0, top edge down → g1 < 0 (screen-up
        // flat ≈ g1 = +9.8 → target stays centered).
        val tx = -(g0 / 9.81f).coerceIn(-1f, 1f) * LIQUID_TILT_MAX_PX
        val ty = (g1 / 9.81f).coerceIn(-1f, 1f) * LIQUID_TILT_MAX_PX

        // Slosh kick from rotation (liquid inertia opposes rotation).
        vx += -ry * dt * LIQUID_KICK
        vy += -rx * dt * LIQUID_KICK
        // Rates decay so a stopped rotation stops pushing.
        rx *= (1f - 6f * dt).coerceAtLeast(0f)
        ry *= (1f - 6f * dt).coerceAtLeast(0f)

        // Damped spring toward the tilt target (underdamped → sloshing).
        vx += (tx - px) * LIQUID_STIFFNESS * dt
        vy += (ty - py) * LIQUID_STIFFNESS * dt
        val damp = exp(-LIQUID_DAMPING * dt)
        vx *= damp
        vy *= damp

        px += vx * dt
        py += vy * dt
        px = px.coerceIn(-LIQUID_TILT_MAX_PX, LIQUID_TILT_MAX_PX)
        py = py.coerceIn(-LIQUID_TILT_MAX_PX, LIQUID_TILT_MAX_PX)

        // Round to half-pixel position / whole-pixel velocity: when the
        // pool settles, state stops changing and recomposition stops.
        val npx = (px * 2).toInt() / 2f
        val npy = (py * 2).toInt() / 2f
        val nvx = vx.toInt().toFloat()
        val nvy = vy.toInt().toFloat()
        val old = out.value
        if (old.offset.x != npx || old.offset.y != npy ||
            old.vel.x != nvx || old.vel.y != nvy
        ) {
            out.value = Drift(Offset(npx, npy), Offset(nvx, nvy))
        }
    }
}
