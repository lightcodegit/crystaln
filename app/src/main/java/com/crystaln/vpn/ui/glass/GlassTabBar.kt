package com.crystaln.vpn.ui.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One destination in the floating glass tab bar. */
data class GlassTab(val icon: ImageVector, val label: String)

/**
 * Floating liquid-glass tab bar, iOS-style: a full pill floating above the
 * content, with the selected destination sitting in a raised lighter glass
 * "droplet" tinted with the accent color.
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
            .liquidGlass(
                RoundedCornerShape(999.dp),
                Glass.tint(isDark, overWallpaper),
                isDark,
            ),
    ) {
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val sel = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val cell = @Composable { content: @Composable () -> Unit ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (sel) {
                                        Modifier
                                            .shadow(6.dp, RoundedCornerShape(999.dp))
                                            .liquidGlass(
                                                RoundedCornerShape(999.dp),
                                                Color.White
                                                    .copy(alpha = if (isDark) 0.32f else 0.85f),
                                                isDark,
                                            )
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) { content() }
                    }
                    cell {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onSelect(index) }
                                .padding(horizontal = 4.dp),
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (sel) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
