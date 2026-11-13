package com.crystaln.vpn.data

import org.json.JSONObject
import java.util.UUID

/** Tunnel protocols supported by CrystalN. */
enum class Protocol(val displayName: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    SOCKS("SOCKS5"),
    HTTP("HTTP"),
    WIREGUARD("WireGuard"),
    AMNEZIAWG("AmneziaWG");

    companion object {
        fun fromNameOrNull(name: String?): Protocol? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * A single server profile.
 *
 * Xray-family profiles keep their protocol-specific options in [fields]
 * (id/password/method, transport settings, TLS/REALITY settings, ...).
 * WireGuard profiles carry a raw wg-quick style config in [wgConfig].
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var protocol: Protocol,
    var address: String = "",
    var port: Int = 0,
    var fields: MutableMap<String, String> = mutableMapOf(),
    var wgConfig: String = "",
) {
    val isWireGuard: Boolean get() = protocol == Protocol.WIREGUARD

    /** WireGuard-family protocols run on the GoBackend, not through Xray. */
    val isWireGuardFamily: Boolean
        get() = protocol == Protocol.WIREGUARD || protocol == Protocol.AMNEZIAWG

    /** Short description line shown under the profile name in lists. */
    fun summary(): String = when (protocol) {
        Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
            wgConfig.lineSequence().firstOrNull { it.startsWith("Endpoint") }
                ?.substringAfter('=')?.trim()?.ifEmpty { null }
                ?: protocol.displayName
        else -> "$address:${if (port > 0) port else "?"} · ${transportLabel()}"
    }

    private fun transportLabel(): String {
        val net = fields["network"] ?: return "tcp"
        val tls = when {
            fields["reality"] == "true" -> "/reality"
            fields["tls"] == "true" -> "/tls"
            else -> ""
        }
        return net + tls
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("protocol", protocol.name)
        put("address", address)
        put("port", port)
        put("fields", JSONObject(fields))
        put("wgConfig", wgConfig)
    }

    companion object {
        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = o.optString("name"),
            protocol = Protocol.fromNameOrNull(o.optString("protocol")) ?: Protocol.VLESS,
            address = o.optString("address"),
            port = o.optInt("port"),
            fields = o.optJSONObject("fields")?.let { jo ->
                mutableMapOf<String, String>().apply {
                    jo.keys().forEach { k -> put(k, jo.optString(k)) }
                }
            } ?: mutableMapOf(),
            wgConfig = o.optString("wgConfig"),
        )
    }
}
