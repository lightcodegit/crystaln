package com.crystaln.vpn.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileImporter
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.data.SubscriptionRefresher
import com.crystaln.vpn.data.SubscriptionsStore
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.desktop.net.Binaries
import com.crystaln.vpn.desktop.net.TunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main window: connection controls, profile list, import, subscriptions,
 * settings and a live log. All state comes from the stores + TunnelManager
 * flows; there is no view model layer — the desktop app is small enough.
 */
@Composable
fun CrystalnApp() {
    val cfg = remember { AppDirs.configDir() }
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf(ProfileStore.load(cfg).toList()) }
    var selectedId by remember { mutableStateOf(ProfileStore.selectedId) }

    val conn by TunnelManager.ui.collectAsState()
    val logLines by TunnelManager.log.collectAsState()

    var mode by remember { mutableStateOf(SettingsStore.tunnelMode(cfg)) }
    var socksPort by remember { mutableStateOf(SettingsStore.socksPort(cfg).toString()) }
    var httpPort by remember { mutableStateOf(SettingsStore.httpPort(cfg).toString()) }
    var dnsServer by remember { mutableStateOf(SettingsStore.dnsServer(cfg)) }
    var dnsViaTunnel by remember { mutableStateOf(SettingsStore.dnsViaTunnel(cfg)) }
    var blockAds by remember { mutableStateOf(SettingsStore.blockAds(cfg)) }

    var importText by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Profile?>(null) }

    fun reload() {
        profiles = ProfileStore.load(cfg).toList()
        selectedId = ProfileStore.selectedId
    }

    // Auto-connect at launch (off by default).
    LaunchedEffect(Unit) {
        if (SettingsStore.autoConnect(cfg)) {
            ProfileStore.selected(cfg)?.let { TunnelManager.connect(it) }
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {

                // ── header: status + connect ─────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = statusText(conn),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (conn.state) {
                                TunnelManager.State.CONNECTED -> Color(0xFF1B7A2E)
                                TunnelManager.State.OFF ->
                                    if (conn.error != null) Color(0xFFB3261E)
                                    else MaterialTheme.colorScheme.onSurface
                                else -> Color(0xFF9A6B00)
                            },
                        )
                        if (conn.error != null && conn.state == TunnelManager.State.OFF) {
                            Text(
                                conn.error!!,
                                fontSize = 12.sp,
                                color = Color(0xFFB3261E),
                            )
                        }
                        if (notice.isNotBlank()) {
                            Text(notice, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    Binaries.downloadAll(needGeosite = blockAds) { step -> notice = step }
                                    notice = "Бинарники готовы: ${binStatus()}"
                                } catch (t: Throwable) {
                                    notice = "Ошибка загрузки: ${t.message ?: t.javaClass.simpleName}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text("Обновить\nбинарники") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (conn.state == TunnelManager.State.OFF) {
                                notice = ""
                                ProfileStore.selected(cfg)?.let { TunnelManager.connect(it) }
                            } else {
                                TunnelManager.stop()
                            }
                        },
                        enabled = conn.state == TunnelManager.State.CONNECTED ||
                            conn.state == TunnelManager.State.OFF ||
                            conn.state == TunnelManager.State.STARTING,
                    ) {
                        Text(
                            when (conn.state) {
                                TunnelManager.State.OFF -> "Подключить"
                                TunnelManager.State.STARTING -> "Подключение…"
                                TunnelManager.State.CONNECTED -> "Отключить"
                                TunnelManager.State.STOPPING -> "Отключение…"
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── settings row ─────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Режим:", fontSize = 13.sp)
                    listOf(
                        SettingsStore.TunnelMode.TUN to "TUN (root)",
                        SettingsStore.TunnelMode.PROXY to "Прокси (без root)",
                    ).forEach { (m, label) ->
                        RadioButton(
                            selected = mode == m,
                            onClick = {
                                mode = m
                                SettingsStore.setTunnelMode(cfg, m)
                            },
                        )
                        Text(label, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = socksPort,
                        onValueChange = {
                            socksPort = it.filter { c -> c.isDigit() }.take(5)
                            socksPort.toIntOrNull()?.let { p -> SettingsStore.setSocksPort(cfg, p) }
                        },
                        label = { Text("SOCKS") },
                        singleLine = true,
                        modifier = Modifier.width(104.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = httpPort,
                        onValueChange = {
                            httpPort = it.filter { c -> c.isDigit() }.take(5)
                            httpPort.toIntOrNull()?.let { p -> SettingsStore.setHttpPort(cfg, p) }
                        },
                        label = { Text("HTTP") },
                        singleLine = true,
                        modifier = Modifier.width(104.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Checkbox(
                        checked = dnsViaTunnel,
                        onCheckedChange = {
                            dnsViaTunnel = it
                            SettingsStore.setDnsViaTunnel(cfg, it)
                        },
                    )
                    Text("DNS через туннель", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = {
                            dnsServer = it
                            SettingsStore.setDnsServer(cfg, it)
                        },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Checkbox(
                        checked = blockAds,
                        onCheckedChange = {
                            blockAds = it
                            SettingsStore.setBlockAds(cfg, it)
                        },
                    )
                    Text("Блокировать рекламу", fontSize = 13.sp)
                }

                Text(
                    "Xray: ${binStatus()}  ·  режим применяется при следующем подключении",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // ── edit form (inline, no dialog) ────────────────────────
                editing?.let { target ->
                    EditProfileForm(
                        target = target,
                        onCancel = { editing = null },
                        onSave = { name, address, port ->
                            ProfileStore.upsert(cfg, target.copy(name = name, address = address, port = port))
                            editing = null
                            reload()
                            notice = "Профиль сохранён"
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // ── profiles ─────────────────────────────────────────────
                Text("Профили (${profiles.size})", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(profiles, key = { it.id }) { p ->
                        ProfileRow(
                            profile = p,
                            selected = p.id == selectedId,
                            onSelect = {
                                ProfileStore.select(cfg, p.id)
                                selectedId = p.id
                            },
                            onEdit = { editing = p },
                            onDelete = {
                                ProfileStore.remove(cfg, p.id)
                                reload()
                                notice = "Профиль удалён"
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ── import ───────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("Ссылки vless://, vmess://, ss://, trojan://, wg/ovpn-конфиг") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val text = importText.trim()
                            if (text.isEmpty()) return@Button
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val parsed = ProfileImporter.parseText(text)
                                    if (parsed.isEmpty()) {
                                        notice = "Ничего не распознано"
                                    } else {
                                        val r = ProfileStore.mergeProfiles(cfg, parsed, null, selectFirst = true)
                                        notice = "Импорт: +${r.added} новых, ${r.updated} обновлено"
                                        importText = ""
                                    }
                                } catch (t: Throwable) {
                                    notice = "Ошибка импорта: ${t.message ?: t.javaClass.simpleName}"
                                }
                                reload()
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text("Импорт") }
                }

                Spacer(Modifier.height(6.dp))

                // ── subscriptions ────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it },
                        label = { Text("URL подписки (http/https)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val url = subUrl.trim()
                            if (url.isEmpty()) return@OutlinedButton
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val sub = SubscriptionsStore.add(cfg, url)
                                    val r = SubscriptionRefresher.refresh(cfg, sub)
                                    notice = if (r.ok) {
                                        "Подписка: +${r.added}, обновлено ${r.updated}"
                                    } else {
                                        "Ошибка подписки: ${r.error ?: "неизвестно"}"
                                    }
                                    subUrl = ""
                                } catch (t: Throwable) {
                                    notice = "Ошибка: ${t.message ?: t.javaClass.simpleName}"
                                }
                                reload()
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text("Добавить") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch(Dispatchers.IO) {
                                val results = SubscriptionRefresher.refreshAll(cfg)
                                notice = if (results.isEmpty()) {
                                    "Нет активных подписок"
                                } else {
                                    "Подписки: " + results.joinToString("; ") { (s, r) ->
                                        "${s.name}: " + if (r.ok) "+${r.added}" else "ошибка (${r.error})"
                                    }
                                }
                                reload()
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text("Обновить") }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))

                // ── log ──────────────────────────────────────────────────
                Text("Журнал", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        ),
                    reverseLayout = true,
                ) {
                    items(logLines.asReversed()) { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        protocolBadge(profile),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        profile.name.ifBlank { "(без имени)" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Text(
                    "${profile.address}:${profile.port}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (profile.subscriptionId != null) {
                Text(
                    "подписка",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            TextButton(onClick = onEdit, enabled = profile.subscriptionId == null) { Text("Изм.") }
            TextButton(onClick = onDelete) { Text("Удал.") }
        }
    }
}

@Composable
private fun EditProfileForm(
    target: Profile,
    onCancel: () -> Unit,
    onSave: (name: String, address: String, port: Int) -> Unit,
) {
    var name by remember(target.id) { mutableStateOf(target.name) }
    var address by remember(target.id) { mutableStateOf(target.address) }
    var port by remember(target.id) { mutableStateOf(target.port.toString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text("Адрес") },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Порт") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                Button(
                    onClick = { onSave(name, address, port.toIntOrNull() ?: target.port) },
                    enabled = name.isNotBlank() && address.isNotBlank(),
                ) { Text("Сохранить") }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    }
}

private fun statusText(conn: TunnelManager.UiState): String = when (conn.state) {
    TunnelManager.State.OFF ->
        if (conn.error != null) "Ошибка: ${conn.error}" else "Отключено"
    TunnelManager.State.STARTING -> "Подключение…"
    TunnelManager.State.CONNECTED -> "Подключено · ${describeMode(conn.mode)}"
    TunnelManager.State.STOPPING -> "Отключение…"
}

private fun describeMode(mode: String) = when (mode) {
    "tun" -> "TUN"
    "proxy" -> "локальный прокси (127.0.0.1)"
    "wg" -> "WireGuard"
    "openvpn" -> "OpenVPN"
    else -> mode
}

private fun protocolBadge(p: Profile): String = when (p.protocol) {
    com.crystaln.vpn.data.Protocol.VMESS -> "VMess"
    com.crystaln.vpn.data.Protocol.VLESS -> "VLESS"
    com.crystaln.vpn.data.Protocol.TROJAN -> "Trojan"
    com.crystaln.vpn.data.Protocol.SHADOWSOCKS -> "SS"
    com.crystaln.vpn.data.Protocol.SOCKS -> "SOCKS"
    com.crystaln.vpn.data.Protocol.HTTP -> "HTTP"
    com.crystaln.vpn.data.Protocol.WIREGUARD -> "WG"
    com.crystaln.vpn.data.Protocol.AMNEZIAWG -> "AWG"
    com.crystaln.vpn.data.Protocol.OPENVPN -> "OVPN"
}

/** Short human-readable binaries availability line. */
private fun binStatus(): String {
    val s = Binaries.status()
    return listOfNotNull(
        "xray " + if (s.xrayReady) "✓" else "✗",
        "tun2socks " + if (s.tun2socksReady) "✓" else "✗",
        "geosite " + if (s.geositeReady) "✓" else "✗",
    ).joinToString(", ")
}
