package com.crystaln.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol

/**
 * Full-screen protocol-aware profile editor.
 *
 * Field sets follow the selected protocol: identity fields, transport fields
 * (shown per transport), TLS/REALITY security fields, and for WireGuard-family
 * protocols a raw wg-quick config editor plus AmneziaWG junk params.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    existing: Profile?,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    var protocol by remember { mutableStateOf(existing?.protocol ?: Protocol.VLESS) }
    val values = remember(protocol) { mutableStateMapOf<String, String>().apply { prefill(this, existing, protocol) } }
    var tls by remember(protocol) { mutableStateOf(existing?.fields?.get("tls") == "true") }
    var reality by remember(protocol) { mutableStateOf(existing?.fields?.get("reality") == "true") }
    var insecure by remember(protocol) { mutableStateOf(existing?.fields?.get("allowInsecure") == "true") }

    fun set(key: String, value: String) {
        values[key] = value
    }

    fun value(key: String): String = values[key] ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Новый профиль" else "Редактор профиля") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (existing == null) {
                SectionLabel("Протокол")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Protocol.entries.forEach { p ->
                        FilterChip(
                            selected = p == protocol,
                            onClick = { protocol = p },
                            label = { Text(p.displayName) },
                        )
                    }
                }
            }

            when {
                protocol == Protocol.WIREGUARD || protocol == Protocol.AMNEZIAWG ->
                    WireGuardFamilyFields(protocol, existing, ::value, ::set)

                else -> XrayFields(protocol, existing, ::value, ::set, tls, reality, insecure,
                    onTls = { tls = it }, onReality = { reality = it }, onInsecure = { insecure = it })
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onSave(buildProfile(existing, protocol, values, tls, reality, insecure))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun prefill(map: MutableMap<String, String>, existing: Profile?, protocol: Protocol) {
    if (existing == null) return
    map["name"] = existing.name
    map["address"] = existing.address
    map["port"] = if (existing.port > 0) existing.port.toString() else ""
    map["wgConfig"] = existing.wgConfig
    if (existing.protocol == protocol) {
        existing.fields.forEach { (k, v) ->
            if (k !in setOf("network", "tls", "reality", "allowInsecure")) map[k] = v
        }
    }
}

private fun buildProfile(
    existing: Profile?,
    protocol: Protocol,
    values: Map<String, String>,
    tls: Boolean,
    reality: Boolean,
    insecure: Boolean,
): Profile {
    fun v(key: String) = values[key]?.trim().orEmpty()
    val name = v("name").ifEmpty { existing?.name ?: "${protocol.displayName} ${v("address")}" }
    val p = existing?.copy() ?: Profile(name = name, protocol = protocol)
    p.name = name
    p.protocol = protocol

    if (protocol == Protocol.WIREGUARD || protocol == Protocol.AMNEZIAWG) {
        p.wgConfig = v("wgConfig")
        p.fields = mutableMapOf()
        if (protocol == Protocol.AMNEZIAWG) {
            listOf("jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4").forEach { k ->
                v(k).takeIf { it.isNotEmpty() }?.let { p.fields[k] = it }
            }
        }
        return p
    }

    p.address = v("address")
    p.port = v("port").toIntOrNull() ?: 0
    p.fields = mutableMapOf()
    listOf(
        "uuid", "alterId", "flow", "password", "method", "username",
        "host", "path", "serviceName", "headerType", "kcpSeed",
        "sni", "fingerprint", "alpn", "publicKey", "shortId", "spiderX",
    ).forEach { k -> v(k).takeIf { it.isNotEmpty() }?.let { p.fields[k] = it } }
    values["network"]?.let { p.fields["network"] = it }
    if (reality) p.fields["reality"] = "true" else if (tls) p.fields["tls"] = "true"
    if (insecure) p.fields["allowInsecure"] = "true"
    return p
}

private fun fieldVisible(key: String, network: String?): Boolean {
    val visible = when (network ?: "tcp") {
        "ws", "httpupgrade", "xhttp", "http" -> setOf("host", "path")
        "grpc" -> setOf("serviceName")
        "kcp" -> setOf("headerType", "kcpSeed")
        "quic" -> setOf("headerType")
        else -> emptySet()
    }
    return key in visible
}

private fun securityVisible(key: String, tls: Boolean, reality: Boolean): Boolean {
    val visible = when {
        reality -> setOf("sni", "fingerprint", "publicKey", "shortId", "spiderX")
        tls -> setOf("sni", "fingerprint", "alpn")
        else -> emptySet()
    }
    return key in visible
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XrayFields(
    protocol: Protocol,
    existing: Profile?,
    value: (String) -> String,
    set: (String, String) -> Unit,
    tls: Boolean,
    reality: Boolean,
    insecure: Boolean,
    onTls: (Boolean) -> Unit,
    onReality: (Boolean) -> Unit,
    onInsecure: (Boolean) -> Unit,
) {
    if (protocol != Protocol.SHADOWSOCKS) {
        TextFieldRow("address", "Адрес (host или IP)", value, set)
        TextFieldRow("port", "Порт", value, set, numeric = true)
    }
    when (protocol) {
        Protocol.VMESS -> {
            TextFieldRow("uuid", "UUID", value, set)
            TextFieldRow("alterId", "alterId", value, set, numeric = true)
        }
        Protocol.VLESS -> {
            TextFieldRow("uuid", "UUID", value, set)
            TextFieldRow("flow", "flow (xtls-rprx-vision / пусто)", value, set)
        }
        Protocol.TROJAN -> TextFieldRow("password", "Пароль", value, set)
        Protocol.SHADOWSOCKS -> {
            TextFieldRow("method", "Метод (aes-256-gcm, chacha20-ietf-poly1305…)", value, set)
            TextFieldRow("password", "Пароль", value, set)
            TextFieldRow("address", "Адрес (host или IP)", value, set)
            TextFieldRow("port", "Порт", value, set, numeric = true)
        }
        Protocol.SOCKS, Protocol.HTTP -> {
            TextFieldRow("username", "Логин (опционально)", value, set)
            TextFieldRow("password", "Пароль (опционально)", value, set)
        }
        Protocol.WIREGUARD, Protocol.AMNEZIAWG -> Unit
    }

    if (protocol in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN, Protocol.SHADOWSOCKS)) {
        SectionLabel("Транспорт")
        TransportChips(existing, value, set)
    }

    SectionLabel("Безопасность")
    ToggleRow("TLS", tls, onTls)
    ToggleRow("REALITY", reality, onReality)
    if (tls || reality) {
        val visible = if (reality) {
            setOf("sni", "fingerprint", "publicKey", "shortId", "spiderX")
        } else {
            setOf("sni", "fingerprint", "alpn")
        }
        listOf("sni", "fingerprint", "alpn", "publicKey", "shortId", "spiderX").forEach { k ->
            if (k in visible) {
                TextFieldRow(k, securityLabel(k), value, set)
            }
        }
        if (!reality) {
            ToggleRow("Разрешить небезопасный TLS", insecure, onInsecure)
        }
    }
}

private fun securityLabel(key: String): String = when (key) {
    "sni" -> "SNI / serverName"
    "fingerprint" -> "uTLS fingerprint"
    "alpn" -> "ALPN"
    "publicKey" -> "REALITY public key"
    "shortId" -> "REALITY shortId"
    "spiderX" -> "REALITY spiderX"
    else -> key
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransportChips(existing: Profile?, value: (String) -> String, set: (String, String) -> Unit) {
    val transports = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp", "quic", "http")
    val current = value("network").ifEmpty { existing?.fields?.get("network") ?: "tcp" }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        transports.forEach { t ->
            FilterChip(
                selected = t == current,
                onClick = { set("network", t) },
                label = { Text(t) },
            )
        }
    }
    val network = value("network").ifEmpty { "tcp" }
    listOf("host", "path", "serviceName", "headerType", "kcpSeed").forEach { k ->
        if (fieldVisible(k, network)) {
            TextFieldRow(k, transportLabel(k), value, set)
        }
    }
}

private fun transportLabel(key: String): String = when (key) {
    "host" -> "Host header"
    "path" -> "Path"
    "serviceName" -> "gRPC service name"
    "headerType" -> "Header type (kcp/quic)"
    "kcpSeed" -> "KCP seed"
    else -> key
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WireGuardFamilyFields(
    protocol: Protocol,
    existing: Profile?,
    value: (String) -> String,
    set: (String, String) -> Unit,
) {
    TextFieldRow(
        "wgConfig",
        if (protocol == Protocol.AMNEZIAWG) "wg-quick конфиг (+ AmneziaWG)" else "wg-quick конфиг",
        value,
        set,
        minLines = 8,
        placeholder = "[Interface]\nPrivateKey = ...\nAddress = 10.0.0.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = ...\nEndpoint = host:port\nAllowedIPs = 0.0.0.0/0, ::/0",
    )
    if (protocol == Protocol.AMNEZIAWG) {
        SectionLabel("Параметры обфускации (AmneziaWG)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("jc" to "Jc", "jmin" to "Jmin", "jmax" to "Jmax", "s1" to "S1", "s2" to "S2").forEach { (k, l) ->
                    TextFieldRow(k, l, value, set, numeric = true, compact = true)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("h1" to "H1", "h2" to "H2", "h3" to "H3", "h4" to "H4").forEach { (k, l) ->
                    TextFieldRow(k, l, value, set, numeric = true, compact = true)
                }
            }
        }
        Text(
            "Ненулевые параметры требуют AmneziaWG-сервера. Пусто = обычный WireGuard.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextFieldRow(
    key: String,
    label: String,
    value: (String) -> String,
    set: (String, String) -> Unit,
    numeric: Boolean = false,
    minLines: Int = 1,
    placeholder: String? = null,
    compact: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!compact) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value(key),
            onValueChange = { set(key, it) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = if (minLines > 1) 14 else minLines,
            singleLine = minLines <= 1,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp),
    )
}
