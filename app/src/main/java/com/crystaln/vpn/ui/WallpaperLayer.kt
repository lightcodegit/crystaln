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
 * Full-screen photo backdrop. The image is heavily blurred — that blur is
 * what makes the glass surfaces above read as real frosted glass — and a
 * soft scrim keeps text readable over any photo. Also drawn as an exact
 * replica behind the floating tab bar (see TabBarBackdrop).
 */
@Composable
fun WallpaperLayer(bitmap: Bitmap, isDark: Boolean) {
    Box(Modifier.fillMaxSize()) {
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
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = if (isDark) 0.40f else 0.18f),
                            Color.Black.copy(alpha = if (isDark) 0.62f else 0.34f),
                        ),
                    ),
                ),
        )
    }
}
