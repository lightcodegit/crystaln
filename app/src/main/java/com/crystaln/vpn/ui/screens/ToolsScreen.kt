package com.crystaln.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.glass.GlassPanel

/** Tools tab: split tunneling, kill-switch, diagnostics and URL-test. */
@Composable
fun ToolsScreen(
    isDark: Boolean,
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    onSplitTunnel: () -> Unit,
    onDiagnostics: () -> Unit,
    onUrlTest: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            "Инструменты",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(16.dp))

        GlassPanel(isDark = isDark) {
            ToolRow(Icons.Default.Apps, "Split tunneling", "Выбор приложений для VPN") { onSplitTunnel() }
            ToolRow(
                Icons.Default.GppGood,
                "Kill-switch",
                "Блокировать интернет при падении VPN",
                trailing = {
                    Switch(checked = killSwitch, onCheckedChange = onKillSwitchChange)
                },
            )
            ToolRow(Icons.Default.Bolt, "Диагностика", "Проверка конфига и доступности") { onDiagnostics() }
            ToolRow(Icons.Default.Speed, "URL-test", "Подключить самый быстрый сервер") { onUrlTest() }
        }
        Spacer(Modifier.height(170.dp))
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) trailing()
    }
}
