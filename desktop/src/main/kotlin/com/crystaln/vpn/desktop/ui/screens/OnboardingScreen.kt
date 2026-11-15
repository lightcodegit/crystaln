package com.crystaln.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.desktop.ui.LiquidBlobs
import com.crystaln.vpn.desktop.ui.glass.PillButton
import com.crystaln.vpn.desktop.ui.glass.SectionLabel

/**
 * First-run wizard, shown once: what the app does → binaries + import a
 * profile → done. Skippable at every step (desktop port of the Android one).
 */
@Composable
fun OnboardingScreen(
    isDark: Boolean,
    step: Int,
    onStep: (Int) -> Unit,
    binariesReady: Boolean,
    binariesBusy: Boolean,
    binariesStatus: String,
    onUpdateBinaries: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onImportUrl: () -> Unit,
    onFinish: () -> Unit,
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
                    Text(
                        when (step) {
                            0 -> "Добро пожаловать в CrystalN"
                            1 -> "Серверы и бинарники"
                            else -> "Всё готово"
                        },
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
                    when (step) {
                        0 -> {
                            SettingsPanel(isDark = isDark) {
                                Text(
                                    "Мультипротокольный VPN-клиент для Linux: локальный прокси " +
                                        "и полноэкранный TUN, умный выбор самого быстрого сервера, " +
                                        "статистика трафика и защита от рекламы.",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Протоколы: Xray (VLESS / VMess / Trojan / Shadowsocks / " +
                                        "SOCKS / HTTP), WireGuard, AmneziaWG, OpenVPN.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SectionLabel("Приватность")
                            SettingsPanel(isDark = isDark) {
                                Text(
                                    "Приложение работает только с серверами, которые вы добавили сами. " +
                                        "Аналитики нет, телеметрии нет.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        1 -> {
                            SettingsPanel(isDark = isDark) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Ядра протоколов",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (binariesReady) "Бинарники готовы: $binariesStatus" else "Статус: $binariesStatus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "xray, tun2socks, amneziawg-go и awg скачиваются с официальных " +
                                        "релизов. Без них не будут работать соответствующие протоколы.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                PillButton(
                                    text = if (binariesBusy) "Загрузка…" else "Обновить бинарники",
                                    icon = Icons.Default.Bolt,
                                    accent = !binariesReady,
                                    isDark = isDark,
                                    enabled = !binariesBusy,
                                    onClick = onUpdateBinaries,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            SettingsPanel(isDark = isDark) {
                                Text(
                                    "Импортируйте конфиг или ссылку от вашего провайдера VPN. " +
                                        "Позже это можно сделать в любой момент кнопкой импорта.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                ImportAction(
                                    icon = Icons.Default.ContentPaste,
                                    label = "Вставить из буфера",
                                    onClick = onImportClipboard,
                                )
                                ImportAction(
                                    icon = Icons.Default.FileOpen,
                                    label = "Открыть файл конфигурации",
                                    onClick = onImportFile,
                                )
                                ImportAction(
                                    icon = Icons.Default.Link,
                                    label = "Ссылка на конфигурацию",
                                    onClick = onImportUrl,
                                )
                            }
                        }

                        else -> {
                            SettingsPanel(isDark = isDark) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Можно подключаться!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Нажмите на большой круг на главном экране, чтобы включить VPN. " +
                                        "Для TUN-режима root запрашивается один раз — дальше сервис " +
                                        "работает без пароля. Автоподключение и другие настройки — в «Настройках».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val last = step >= 2
                PillButton(
                    text = if (step == 0) "Далее" else "Пропустить",
                    icon = if (last) Icons.Default.Bolt else null,
                    accent = last,
                    isDark = isDark,
                    onClick = { if (last) onFinish() else onStep(step + 1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ImportAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(label)
        }
    }
}
