package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileShareLink
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.data.TrafficStats
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.ui.glass.CircleGlassButton
import com.crystaln.vpn.desktop.ui.glass.CountBadge
import com.crystaln.vpn.desktop.ui.glass.Glass
import com.crystaln.vpn.desktop.ui.glass.GlassTab
import com.crystaln.vpn.desktop.ui.glass.GlassTabBar
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.liquidGlass
import com.crystaln.vpn.desktop.resourcePainter
import com.crystaln.vpn.desktop.ui.theme.LocalCrystalPalette
import com.crystaln.vpn.desktop.ui.theme.StatusConnected
import com.crystaln.vpn.desktop.ui.theme.StatusConnecting
import com.crystaln.vpn.desktop.ui.theme.StatusOff
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * Share dialog: QR code (for scanning into another device) plus the raw
 * share text with a copy action. Profiles without shareable content (e.g. a
 * WG config that was never filled) say so instead.
 */
@Composable
internal fun ShareProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val text = remember(profile.id) { ProfileShareLink.shareText(profile) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поделиться профилем") },
        text = {
            Column {
                if (text == null) {
                    Text(
                        "Этот профиль нельзя экспортировать: нет адреса или учётных данных.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (text.length <= QR_MAX_TEXT) {
                        val qr = remember(text) { qrImageBitmap(text) }
                        Image(
                            bitmap = qr,
                            contentDescription = "QR code",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (copied) {
                        Text(
                            "Ссылка скопирована",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (text != null) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Копировать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

/** Beyond this, QR density stops being scannable; fall back to text-only. */
private const val QR_MAX_TEXT = 2000

/** Black-on-white QR of [content], quiet zone 1 module. */
private fun qrImageBitmap(content: String): ImageBitmap {
    val size = 780
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) {
        for (x in 0 until size) {
            img.setRGB(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return img.toComposeImageBitmap()
}
