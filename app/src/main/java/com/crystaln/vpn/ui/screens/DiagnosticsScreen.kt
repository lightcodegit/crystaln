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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.R
import com.crystaln.vpn.data.ConnectionDiagnostics
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.ui.LiquidBlobs
import com.crystaln.vpn.ui.WallpaperLayer
import com.crystaln.vpn.ui.glass.CircleGlassButton
import com.crystaln.vpn.ui.glass.Glass
import com.crystaln.vpn.ui.glass.PillButton
import com.crystaln.vpn.ui.glass.SectionLabel
import com.crystaln.vpn.ui.glass.liquidGlass
import com.crystaln.vpn.ui.rememberWallpaper
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * "Why doesn't it connect?" screen: DNS, TCP and config validation for the
 * selected profile. Read-only — checks never start or stop the VPN.
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
    val wallpaper = rememberWallpaper(SettingsStore.wallpaper(LocalContext.current))
    val glassDark = wallpaper?.isDark ?: isDark
    val overWallpaper = wallpaper != null

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val hazeState = rememberHazeState()

        Box(Modifier.matchParentSize().hazeSource(hazeState)) {
            if (wallpaper != null) {
                WallpaperLayer(wallpaper.bitmap, wallpaper.isDark, themeDark = isDark)
            } else {
                LiquidBlobs(isDark)
            }

            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleGlassButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            isDark = glassDark,
                            overWallpaper = overWallpaper,
                            onClick = onBack,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.diag_title),
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
                        SectionLabel(stringResource(R.string.diag_target))
                        SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                            Text(
                                profileName.ifEmpty { stringResource(R.string.no_profile) },
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
                                stringResource(
                                    if (vpnRunning) R.string.diag_vpn_active else R.string.diag_vpn_idle,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // ── Checks ─────────────────────────────────────
                        SectionLabel(stringResource(R.string.diag_checks))
                        SettingsPanel(isDark = glassDark, overWallpaper = overWallpaper) {
                            if (running) {
                                Text(
                                    stringResource(R.string.diag_running),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            results.orEmpty().forEachIndexed { i, r ->
                                if (i > 0) {
                                    androidx.compose.material3.HorizontalDivider(
                                        Modifier.padding(vertical = 12.dp),
                                    )
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
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            if (r.ok) {
                                                stringResource(R.string.diag_status_ok)
                                            } else {
                                                stringResource(R.string.diag_status_fail)
                                            },
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
                                                stringResource(R.string.ping_ms, r.ms.toInt()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (!running && (results == null || results.isEmpty())) {
                                Text(
                                    stringResource(R.string.diag_not_run),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Text(
                            stringResource(R.string.diag_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(4.dp))
                        PillButton(
                            text = stringResource(R.string.diag_run),
                            icon = Icons.Default.Bolt,
                            accent = true,
                            isDark = glassDark,
                            overWallpaper = overWallpaper,
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
}

@Composable
private fun checkTitle(kind: ConnectionDiagnostics.Kind): String = when (kind) {
    ConnectionDiagnostics.Kind.DNS -> stringResource(R.string.diag_dns)
    ConnectionDiagnostics.Kind.TCP -> stringResource(R.string.diag_tcp)
    ConnectionDiagnostics.Kind.ICMP -> stringResource(R.string.diag_icmp)
    ConnectionDiagnostics.Kind.CONFIG -> stringResource(R.string.diag_config)
}
