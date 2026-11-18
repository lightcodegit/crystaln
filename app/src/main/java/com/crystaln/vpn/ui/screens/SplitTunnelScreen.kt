package com.crystaln.vpn.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.glass.GlassTabBar
import com.crystaln.vpn.ui.glass.GlassTab

/** Split tunneling: choose apps that bypass or exclusively use the VPN. */
@Composable
fun SplitTunnelScreen(
    isDark: Boolean,
    mode: Int,
    selected: Set<String>,
    onModeChange: (Int) -> Unit,
    onToggleApp: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val apps = remember {
        ctx.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { ctx.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { ctx.packageManager.getApplicationLabel(it).toString() }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleGlassButton(icon = Icons.Default.ArrowBack, contentDescription = "Back", isDark = isDark, onClick = onBack, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text("Split tunneling", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Выкл", 1 to "Исключить", 2 to "Только выбранные").forEach { (m, label) ->
                androidx.compose.material3.FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (mode) {
                1 -> "Выбранные приложения пойдут мимо VPN"
                2 -> "Только выбранные приложения пойдут через VPN"
                else -> "Весь трафик идёт через VPN"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { ai ->
                val label = ctx.packageManager.getApplicationLabel(ai).toString()
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleApp(ai.packageName) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ai.packageName in selected, onCheckedChange = { onToggleApp(ai.packageName) })
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
