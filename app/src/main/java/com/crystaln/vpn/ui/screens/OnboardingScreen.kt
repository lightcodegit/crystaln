package com.crystaln.vpn.ui.screens

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.R
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.glass.PillButton
import com.crystaln.vpn.ui.glass.SectionLabel

/**
 * First-run wizard, shown once: what the app does → import a profile
 * (re-uses MainActivity import actions) → done. Skippable at every step.
 */
@Composable
fun OnboardingScreen(
    isDark: Boolean,
    step: Int,
    onStep: (Int) -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onImportQr: () -> Unit,
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
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (step) {
                            0 -> stringResource(R.string.onboarding_title)
                            1 -> stringResource(R.string.onboarding_import_title)
                            else -> stringResource(R.string.onboarding_done_title)
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
                            SettingsPanel(isDark = isDark, overWallpaper = false) {
                                Text(
                                    stringResource(R.string.onboarding_welcome_body),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.onboarding_protocols),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            SectionLabel(stringResource(R.string.onboarding_privacy))
                            SettingsPanel(isDark = isDark, overWallpaper = false) {
                                Text(
                                    stringResource(R.string.onboarding_privacy_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        1 -> {
                            SettingsPanel(isDark = isDark, overWallpaper = false) {
                                Text(
                                    stringResource(R.string.onboarding_import_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                ImportAction(
                                    icon = Icons.Default.ContentPaste,
                                    label = stringResource(R.string.import_clipboard),
                                    onClick = onImportClipboard,
                                )
                                ImportAction(
                                    icon = Icons.Default.FileOpen,
                                    label = stringResource(R.string.import_file_action),
                                    onClick = onImportFile,
                                )
                                ImportAction(
                                    icon = Icons.Default.QrCodeScanner,
                                    label = stringResource(R.string.import_qr),
                                    onClick = onImportQr,
                                )
                                ImportAction(
                                    icon = Icons.Default.Link,
                                    label = stringResource(R.string.import_url),
                                    onClick = onImportUrl,
                                )
                            }
                        }

                        else -> {
                            SettingsPanel(isDark = isDark, overWallpaper = false) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.onboarding_done_body),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.onboarding_done_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val last = step >= 2
                PillButton(
                    text = stringResource(
                        if (step == 0) R.string.onboarding_next else R.string.onboarding_skip,
                    ),
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
private fun ImportAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
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
