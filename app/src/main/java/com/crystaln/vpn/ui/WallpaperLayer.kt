package com.crystaln.vpn.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Full-screen photo backdrop. The photo itself is drawn FIT — the whole image
 * is visible, never zoomed or cropped (a cover-fill used to enlarge landscape
 * photos several times over on tall screens). Behind it a heavily blurred
 * cover-filled copy of the same photo pads any leftover bands and is what
 * makes the glass surfaces above read as real frosted glass. A soft scrim
 * keeps text readable over any photo. The scrim follows the chosen theme: a
 * dark theme darkens the photo, a light one frosts it white so dark text
 * stays readable over dark photos.
 */
@Composable
fun WallpaperLayer(bitmap: Bitmap, isDark: Boolean, themeDark: Boolean = isDark) {
    Box(Modifier.fillMaxSize()) {
        // Decorative blurred backdrop: cover-fills the screen so there are no
        // letterbox bands, and provides the blurred source for the glass.
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // Scale up so the blur's transparent edge falloff stays
                // outside the screen.
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                }
                .then(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Modifier.blur(48.dp)
                    } else {
                        Modifier
                    },
                ),
        )
        // The photo itself: whole image, fitted, never zoomed in.
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (themeDark) {
                        // Dark theme: darken the photo for light text.
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = if (isDark) 0.40f else 0.18f),
                                Color.Black.copy(alpha = if (isDark) 0.62f else 0.34f),
                            ),
                        )
                    } else {
                        // Light theme: frost the photo white for dark text.
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.80f),
                            ),
                        )
                    },
                ),
        )
    }
}
