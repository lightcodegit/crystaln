package com.crystaln.vpn.desktop.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One destination in the floating glass tab bar. */
data class GlassTab(val icon: ImageVector, val label: String)

/**
 * Floating liquid-glass tab bar, iOS-style: a full pill floating above the
 * content, with the selected destination marked by a raised lighter glass
 * "droplet" that flows between tabs with a soft spring animation.
 *
 * Desktop port: without the Haze backdrop blur — the translucent
 * [Glass.tintFloating] fill carries the glass look on its own.
 */
@Composable
fun GlassTabBar(
    isDark: Boolean,
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(999.dp))
            .liquidGlass(
                RoundedCornerShape(999.dp),
                Glass.tintFloating(isDark, overWallpaper),
                isDark,
                flat = true,
            ),
    ) {
        // The droplet: one shared surface that slides between tabs.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cellWidth = maxWidth / tabs.size
            val dropletX by animateDpAsState(
                targetValue = cellWidth * selected,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dropletX",
            )
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
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
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
