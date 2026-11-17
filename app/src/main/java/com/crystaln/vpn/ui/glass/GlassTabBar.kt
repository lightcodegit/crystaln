package com.crystaln.vpn.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** One destination in the floating glass tab bar. */
data class GlassTab(val icon: ImageVector, val label: String)

/**
 * Floating liquid-glass tab bar, iOS-style: a full pill floating above the
 * content, with the selected destination marked by a raised lighter glass
 * "droplet" that flows between tabs with a soft spring animation.
 *
 * When [hazeState] is provided, the bar blurs the real content behind it
 * (Haze) — cards scrolling underneath dissolve into genuine frost.
 */
@Composable
fun GlassTabBar(
    isDark: Boolean,
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
    hazeState: HazeState? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 42.dp
                        noiseFactor = 0.05f
                        tints = listOf(HazeTint(Glass.tintFloating(isDark, overWallpaper)))
                    }
                } else {
                    Modifier
                },
            )
            .liquidGlass(
                RoundedCornerShape(999.dp),
                if (hazeState != null) {
                    // The Haze tint already provides contrast; keep the
                    // glass fill minimal so the blur shows through.
                    if (isDark) DarkGlassTint.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
                } else {
                    Glass.tintFloating(isDark, overWallpaper)
                },
                isDark,
                flat = true,
            ),
    ) {
        // The droplet: one shared surface that slides between tabs,
        // stretching like jelly while it travels and squashing back
        // into place when it arrives.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellWidth = maxWidth / tabs.size
            val dropletX by animateDpAsState(
                targetValue = cellWidth * selected,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dropletX",
            )
            // How far the droplet still has to travel → jelly stretch.
            val travel = (cellWidth * selected - dropletX).value
            val stretch = (kotlin.math.abs(travel) / cellWidth.value * 0.30f)
                .coerceAtMost(0.30f)
            // A hint of the accent color tints the droplet like light passing
            // through glass.
            val dropletFill by animateColorAsState(
                targetValue = if (isDark) {
                    lerp(Color.White, MaterialTheme.colorScheme.primary, 0.20f)
                        .copy(alpha = 0.42f)
                } else {
                    lerp(Color.White, MaterialTheme.colorScheme.primary, 0.08f)
                        .copy(alpha = 0.85f)
                },
                animationSpec = tween(250),
                label = "dropletFill",
            )
            Box(
                Modifier
                    .offset(x = dropletX)
                    .width(cellWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .graphicsLayer {
                        scaleX = 1f + stretch
                        scaleY = 1f - stretch * 0.35f
                    }
                    .shadow(6.dp, RoundedCornerShape(999.dp))
                    .liquidGlass(RoundedCornerShape(999.dp), dropletFill, isDark, flat = true),
            )
        }

        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val sel = index == selected
                val accent = MaterialTheme.colorScheme.primary
                val idle = MaterialTheme.colorScheme.onSurfaceVariant
                val tint by animateColorAsState(
                    targetValue = if (sel) accent else idle,
                    animationSpec = tween(250),
                    label = "tabTint",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                ) {
                    // The active tab's icon pops up with a soft spring bump.
                    val iconScale by animateFloatAsState(
                        targetValue = if (sel) 1.12f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "tabIconScale",
                    )
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                    )
                }
            }
        }
    }
}
