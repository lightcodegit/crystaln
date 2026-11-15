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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.ConnectionDiagnostics
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.ui.glass.CircleGlassButton
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.SectionLabel

/**
 * "Why doesn't it connect?" screen: DNS, TCP and config validation for the
 * selected profile. Read-only — checks never start or stop the VPN.
 * Desktop port of the Android screen.
 *
 * [results] null = checks have not run yet; [running] shows the progress phase.
 */
@Composable
fun DiagnosticsScreen(
    isDark: Boolean,
    profileName: String,
    protocolName: String,
    addressPort: String,
    vpnRunning: Boolean,
    results: List<ConnectionDiagnostics.Result>?,
    running: Boolean,
    onRun: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LiquidBlobs(isDark)

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleGlassButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        isDark = isDark,
                        onClick = onBack,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Диагностика",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Target profile ─────────────────────────────
                    SectionLabel("Проверяемый профиль")
                    SettingsPanel(isDark = isDark) {
                        Text(
                            profileName.ifEmpty { "Нет профиля" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (addressPort.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$protocolName · $addressPort",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (vpnRunning) {
                                "VPN сейчас подключён (другой профиль) — проверки безопасны."
                            } else {
                                "VPN отключён."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // ── Checks ─────────────────────────────────────
                    SectionLabel("Проверки")
                    SettingsPanel(isDark = isDark) {
                        if (running) {
                            Text(
                                "Проверка…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        results.orEmpty().forEachIndexed { i, r ->
                            if (i > 0) {
                                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        checkTitle(r.kind),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (r.detail.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            r.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (r.ok) "OK" else "Ошибка",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (r.ok) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                    if (r.ms >= 0) {
                                        Text(
                                            "${r.ms.toInt()} мс",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (!running && (results == null || results.isEmpty())) {
                            Text(
                                "Проверки ещё не запускались",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        "Проверки не устанавливают VPN-соединение и не отправляют трафик через туннель.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = "Запустить проверку",
                        icon = Icons.Default.Bolt,
                        accent = true,
                        isDark = isDark,
                        enabled = !running,
                        onClick = onRun,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun checkTitle(kind: ConnectionDiagnostics.Kind): String = when (kind) {
    ConnectionDiagnostics.Kind.DNS -> "DNS-резолв адреса"
    ConnectionDiagnostics.Kind.TCP -> "TCP-подключение к серверу"
    ConnectionDiagnostics.Kind.CONFIG -> "Полнота конфигурации"
}
