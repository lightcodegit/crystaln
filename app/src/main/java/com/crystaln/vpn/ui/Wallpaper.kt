package com.crystaln.vpn.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom background image support. The image is decoded once, sampled down
 * to roughly the screen size so a 50 MP photo doesn't allocate 200 MB of RAM.
 *
 * The sample target follows the real screen size: a fixed 1600 px cap used to
 * shrink wide photos to a tiny height, which made the cover-fit backdrop zoom
 * them several times over.
 */
object WallpaperLoader {

    /** Floor for the longest decoded edge: keeps image quality on tablets. */
    private const val MIN_DIMENSION = 1600

    /** Ceiling, so a 50 MP photo still can't allocate 200 MB of RAM. */
    private const val MAX_DIMENSION = 3200

    fun decode(file: File, screenLongSide: Int): Bitmap? {
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // Sample just below the screen size (rotation-proof floor of 1600),
            // never above 3200 px on the longest edge.
            val target = maxOf(MIN_DIMENSION, minOf(screenLongSide, MAX_DIMENSION))
            var sample = 1
            var maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** True when the average luminance is low — glass adapts to dark photos. */
    fun isDark(bmp: Bitmap): Boolean {
        val stepX = maxOf(1, bmp.width / 32)
        val stepY = maxOf(1, bmp.height / 32)
        var sum = 0.0
        var n = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                sum += (android.graphics.Color.red(p) * 0.299 +
                    android.graphics.Color.green(p) * 0.587 +
                    android.graphics.Color.blue(p) * 0.114) / 255.0
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) false else sum / n < 0.45
    }
}

/** A loaded wallpaper plus its average darkness, for adaptive glass. */
data class WallpaperState(val bitmap: Bitmap, val isDark: Boolean)

/**
 * Loads the wallpaper file from internal storage, off the main thread.
 * Decoded at the real screen size so the backdrop never has to be zoomed in.
 */
@Composable
fun rememberWallpaper(fileName: String?): WallpaperState? {
    val context = LocalContext.current
    val screenLongSide = remember {
        val m = context.resources.displayMetrics
        maxOf(m.widthPixels, m.heightPixels)
    }
    return produceState<WallpaperState?>(initialValue = null, fileName) {
        value = if (fileName == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                WallpaperLoader.decode(File(context.filesDir, fileName), screenLongSide)?.let { bmp ->
                    WallpaperState(bmp, WallpaperLoader.isDark(bmp))
                }
            }
        }
    }.value
}
