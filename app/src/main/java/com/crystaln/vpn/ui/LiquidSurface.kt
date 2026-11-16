package com.crystaln.vpn.ui

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.glass.rememberBreath

/**
 * AGSL metaball field: every blob contributes r²/d²-like falloff (an
 * elliptical one when moving), the summed field is thresholded — so blobs
 * bulge toward each other and merge with liquid necks instead of staying
 * hard circles. Velocity stretches each blob along its motion (squash &
 * stretch), like a moving drop. Colors are contribution-weighted, so a
 * merged region shows both palette colors mixed.
 */
private const val METABALL_SHADER = """
uniform float uT;
uniform float uE;
uniform float2 uP1; uniform float uR1; uniform float3 uC1; uniform float2 uV1;
uniform float2 uP2; uniform float uR2; uniform float3 uC2; uniform float2 uV2;
uniform float2 uP3; uniform float uR3; uniform float3 uC3; uniform float2 uV3;
uniform float2 uP4; uniform float uR4; uniform float3 uC4; uniform float2 uV4;

float field(float2 p, float2 c, float r, float2 v) {
    float sp = length(v);
    float s = 1.0 + min(sp / 700.0, 0.6);
    float2 dir = sp < 1.0 ? float2(1.0, 0.0) : v / sp;
    float2 d = p - c;
    float along = dot(d, dir);
    float across = dot(d, float2(-dir.y, dir.x));
    float2 q = float2(along / (r * s), across / r);
    return 1.0 / (dot(q, q) + 0.1);
}

half4 main(float2 fragCoord) {
    float f1 = field(fragCoord, uP1, uR1, uV1);
    float f2 = field(fragCoord, uP2, uR2, uV2);
    float f3 = field(fragCoord, uP3, uR3, uV3);
    float f4 = field(fragCoord, uP4, uR4, uV4);
    float f = f1 + f2 + f3 + f4;
    float a = smoothstep(uT - uE, uT + uE, f);
    float3 col = (f1 * uC1 + f2 * uC2 + f3 * uC3 + f4 * uC4) / max(f, 0.0001);
    return half4(col * a, a);
}
"""

/** Cached AGSL support check: compiling the shader once is cheap and
 *  shields LiquidBlobs from a runtime failure (a broken GPU driver would
 *  otherwise crash inside the composable). */
private var metaballsSupportedCache: Boolean? = null

fun metaballsSupported(): Boolean {
    metaballsSupportedCache?.let { return it }
    val ok = Build.VERSION.SDK_INT >= 33 &&
        runCatching { RuntimeShader(METABALL_SHADER) }.isSuccess
    metaballsSupportedCache = ok
    return ok
}

/**
 * The real liquid surface behind the content ("Жидкость" mode, API 33+).
 * Blob centers follow the liquid physics of [rememberLiquidDrift]: they
 * pool toward the tilted side, slosh from shakes, and the shader melts
 * the circles into one continuous fluid.
 */
@RequiresApi(33)
@Composable
fun LiquidSurface(isDark: Boolean) {
    // Same palette colors/alphas as the circle fallback.
    val c1 = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.50f else 0.26f)
    val c2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.38f else 0.20f)
    val c3 = MaterialTheme.colorScheme.tertiary.copy(alpha = if (isDark) 0.34f else 0.18f)
    val c4 = MaterialTheme.colorScheme.primary.copy(alpha = (if (isDark) 0.50f else 0.26f) * 0.75f)

    val liquid = rememberLiquidDrift(true)
    val shader = remember { RuntimeShader(METABALL_SHADER) }
    val paint = remember { Paint() }

    // Slow ambient drift keeps the surface alive even at rest.
    val t = rememberInfiniteTransition(label = "liquid")
    val drift1 by t.animateFloat(
        -64f, 64f,
        infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ld1",
    )
    val drift2 by t.animateFloat(
        48f, -48f,
        infiniteRepeatable(tween(11500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ld2",
    )
    val drift3 by t.animateFloat(
        -40f, 40f,
        infiniteRepeatable(tween(13500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ld3",
    )
    val breathe = rememberBreath(4200)

    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val d = liquid.value
                val o = d.offset
                val v = d.vel

                fun blob(bx: Float, by: Float, k: Float) = Offset(
                    bx.dp.toPx() + o.x * k,
                    by.dp.toPx() + o.y * k,
                )
                // Same base layout as the circle fallback; k = how strongly
                // this blob follows the pool (heavier blobs move less).
                val p1 = blob(-160f + drift1, -200f + drift1 * 0.6f, 1.0f)
                val p2 = blob(260f + drift2, 160f - drift2 * 0.8f, -1.0f)
                val p3 = blob(-110f - drift3, 620f + drift3, 0.7f)
                val p4 = blob(70f + drift2 * 0.5f, 960f - drift1 * 0.4f, -0.5f)
                val v1 = v
                val v2 = v * 0.7f
                val v3 = v * 0.5f
                val v4 = v * 0.85f

                shader.setFloatUniform("uT", 0.909f)
                shader.setFloatUniform("uE", 0.05f)
                shader.setFloatUniform("uP1", p1.x, p1.y)
                shader.setFloatUniform("uR1", 230.dp.toPx())
                shader.setFloatUniform("uC1", c1.red, c1.green, c1.blue)
                shader.setFloatUniform("uV1", v1.x, v1.y)
                shader.setFloatUniform("uP2", p2.x, p2.y)
                shader.setFloatUniform("uR2", 200.dp.toPx())
                shader.setFloatUniform("uC2", c2.red, c2.green, c2.blue)
                shader.setFloatUniform("uV2", v2.x, v2.y)
                shader.setFloatUniform("uP3", p3.x, p3.y)
                shader.setFloatUniform("uR3", 170.dp.toPx())
                shader.setFloatUniform("uC3", c3.red, c3.green, c3.blue)
                shader.setFloatUniform("uV3", v3.x, v3.y)
                shader.setFloatUniform("uP4", p4.x, p4.y)
                shader.setFloatUniform("uR4", (190 + breathe * 25).dp.toPx())
                shader.setFloatUniform("uC4", c4.red, c4.green, c4.blue)
                shader.setFloatUniform("uV4", v4.x, v4.y)

                paint.shader = shader
                drawContext.canvas.nativeCanvas.drawPaint(paint)
            },
    )
}
