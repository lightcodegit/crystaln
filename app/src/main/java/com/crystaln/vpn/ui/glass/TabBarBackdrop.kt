package com.crystaln.vpn.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.WallpaperState

/** A rectangle covering only the bottom 230dp of the node. */
private val stripShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(
        Rect(0f, size.height - with(density) { 230.dp.toPx() }, size.width, size.height),
    )
}

/**
 * Backdrop drawn behind the floating tab bar:
 *
 *  - with a wallpaper: an exact full-screen replica of the wallpaper
 *    backdrop (same layout and blur as the real one), clipped to the
 *    bottom strip, so the glass refracts a truly blurred image and any
 *    content that slides under the bar dissolves into the frost instead
 *    of ghosting through. Because the replica is laid out identically to
 *    the real backdrop, the clip edge is invisible;
 *  - then a gentle fade, kept light enough that the blur still shows.
 *
 * Draw this AFTER the screen content so it covers it, and keep floating
 * controls (FABs, the tab bar itself) after it.
 */
@Composable
fun TabBarBackdrop(
    wallpaper: WallpaperState?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clip(stripShape)) {
        if (wallpaper != null) {
            WallpaperLayer(wallpaper.bitmap, wallpaper.isDark)
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            (if (wallpaper != null) Color.Black else MaterialTheme.colorScheme.background)
                                .copy(alpha = if (wallpaper != null) 0.50f else 0.60f),
                        ),
                    ),
                ),
        )
    }
}
