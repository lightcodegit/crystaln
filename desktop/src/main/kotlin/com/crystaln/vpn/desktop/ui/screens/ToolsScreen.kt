package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.ui.glass.GlassTab
import com.crystaln.vpn.desktop.ui.glass.GlassTabBar
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.SectionLabel
import com.crystaln.vpn.desktop.ui.glass.GlassDivider
import com.crystaln.vpn.desktop.ui.screens.SettingsPanel
import com.crystaln.vpn.desktop.ui.screens.SwitchRow
import com.crystaln.vpn.data.SettingsStore

@Composable
fun ToolsScreen(
    isDark: Boolean,
    killSwitch: Boolean,
    onKillSwitchChange: (Boolean) -> Unit,
    splitTunnelMode: SettingsStore.SplitTunnelMode,
    onSplitTunnelModeChange: (SettingsStore.SplitTunnelMode) -> Unit,
    splitTunnelRules: List<String>,
    onSplitTunnelRulesChange: (List<String>) -> Unit,
    urlTestBusy: Boolean,
    onUrlTest: () -> Unit,
    onPingTool: () -> Unit,
    onDiagnostics: () -> Unit,
    onBack: () -> Unit,
    onMain: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    var splitTunnelText by remember(splitTunnelRules) { mutableStateOf(splitTunnelRules.joinToString("\n")) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LiquidBlobs(isDark)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Инструменты",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Kill-switch
                SectionLabel("Kill-switch")
                SettingsPanel(isDark = isDark) {
                    SwitchRow(
                        title = "Kill-switch",
                        subtitle = "При падении VPN блокировать трафик, кроме VPN-интерфейса (нужны права root/администратора)",
                        checked = killSwitch,
                        onChange = onKillSwitchChange,
                    )
                }

                // Diagnostics
                SectionLabel("Диагностика")
                SettingsPanel(isDark = isDark) {
                    PillButton(
                        text = "Диагностика",
                        icon = Icons.Default.Bolt,
                        accent = false,
                        isDark = isDark,
                        onClick = onDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // URL-test
                SectionLabel("URL-тест")
                SettingsPanel(isDark = isDark) {
                    PillButton(
                        text = if (urlTestBusy) "Тестирую…" else "URL-тест: подключить самый быстрый",
                        icon = Icons.Default.Speed,
                        accent = true,
                        isDark = isDark,
                        enabled = !urlTestBusy,
                        onClick = onUrlTest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Ping tool
                SectionLabel("Ping")
                SettingsPanel(isDark = isDark) {
                    PillButton(
                        text = "Ping-инструмент",
                        icon = Icons.Default.NetworkCheck,
                        accent = false,
                        isDark = isDark,
                        onClick = onPingTool,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Split tunneling
                SectionLabel("Split tunneling")
                SettingsPanel(isDark = isDark) {
                    Text(
                        "Какие адреса идут через туннель (для Xray-профилей). Домен, IP или CIDR — по одному в строке.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "Выкл" to SettingsStore.SplitTunnelMode.OFF,
                            "Исключить" to SettingsStore.SplitTunnelMode.EXCLUDE,
                            "Только эти" to SettingsStore.SplitTunnelMode.ONLY,
                        ).forEach { (label, mode) ->
                            PillButton(
                                text = label,
                                icon = null,
                                accent = splitTunnelMode == mode,
                                isDark = isDark,
                                onClick = { onSplitTunnelModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (splitTunnelMode != SettingsStore.SplitTunnelMode.OFF) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = splitTunnelText,
                            onValueChange = { v ->
                                splitTunnelText = v
                                onSplitTunnelRulesChange(v.lines().map { it.trim() }.filter { it.isNotEmpty() })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("example.com\n10.0.0.0/8") },
                            minLines = 3,
                            maxLines = 8,
                        )
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }

        GlassTabBar(
            isDark = isDark,
            tabs = listOf(
                GlassTab(Icons.Default.PowerSettingsNew, "Подключение"),
                GlassTab(Icons.Default.Build, "Инструменты"),
                GlassTab(Icons.Default.Settings, "Настройки"),
            ),
            selected = 1,
            onSelect = { index ->
                when (index) {
                    0 -> onMain()
                    2 -> onSettings()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}
