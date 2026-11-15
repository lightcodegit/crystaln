package com.crystaln.vpn.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystaln.vpn.ui.theme.LocalCrystalPalette

/** Small uppercase section header with an accent dot, drawn OUTSIDE panels. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Rounded glass panel holding the rows of one content section. */
@Composable
fun GlassPanel(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(26.dp), Glass.tint(isDark, overWallpaper), isDark)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Hairline row separator inside glass panels. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    )
}

/**
 * Segmented glass control with a sliding accent droplet — used for the
 * theme switcher in settings. Compact sibling of GlassTabBar.
 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .liquidGlass(
                RoundedCornerShape(999.dp),
                Glass.tintSoft(isDark, overWallpaper),
                isDark,
                flat = true,
            ),
    ) {
        val cell = maxWidth / options.size
        val dropletX by animateDpAsState(
            targetValue = cell * selected,
            animationSpec = spring(
                dampingRatio = 0.80f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "segmentX",
        )
        Box(
            Modifier
                .offset(x = dropletX)
                .width(cell)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val sel = index == selected
                val color by animateColorAsState(
                    targetValue = if (sel) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "segmentColor",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Pill button: accent (gradient fill) or ghost (glass + rim). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector?,
    accent: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(999.dp)
    val palette = LocalCrystalPalette.current
    val contentColor = if (accent) Color.White else MaterialTheme.colorScheme.primary
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .then(
                if (accent) {
                    Modifier.background(Brush.linearGradient(palette.gradient(isDark)))
                } else {
                    Modifier
                        .background(Glass.tintSoft(isDark, overWallpaper))
                        .border(1.dp, Glass.rim(isDark), shape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}



/** Round glass icon button (back, add, import). */
@Composable
fun CircleGlassButton(
    icon: ImageVector,
    contentDescription: String,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overWallpaper: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 42.dp,
) {
    val palette = LocalCrystalPalette.current
    Box(
        modifier
            .graphicsLayer { this.alpha = if (enabled) 1f else 0.45f }
            .size(size)
            .clip(CircleShape)
            .then(
                if (accent) {
                    Modifier.background(Brush.linearGradient(palette.gradient(isDark)))
                } else {
                    Modifier
                        .liquidGlass(CircleShape, Glass.tintSoft(isDark, overWallpaper), isDark, flat = true)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (accent) Color.White else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Small rounded counter badge, e.g. the profile count in a section header. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
