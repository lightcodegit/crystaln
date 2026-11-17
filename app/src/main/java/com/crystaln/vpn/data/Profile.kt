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
    HTTPS("HTTPS"),
    WIREGUARD("WireGuard"),
    AMNEZIAWG("AmneziaWG"),
    OPENVPN("OpenVPN");

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
 * OpenVPN profiles carry a raw .ovpn config in [ovpnConfig].
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var protocol: Protocol,
    var address: String = "",
    var port: Int = 0,
    var fields: MutableMap<String, String> = mutableMapOf(),
    var wgConfig: String = "",
    var ovpnConfig: String = "",
    /** Null for manually imported profiles; set when owned by a subscription. */
    var subscriptionId: String? = null,
    /** Pinned to the top of the profile list ("favorites"). */
    var favorite: Boolean = false,
    /** Optional user-defined group name (see GroupStore). */
    var group: String? = null,
) {
    val isWireGuard: Boolean get() = protocol == Protocol.WIREGUARD

    /** WireGuard-family protocols run on the GoBackend, not through Xray. */
    val isWireGuardFamily: Boolean
        get() = protocol == Protocol.WIREGUARD || protocol == Protocol.AMNEZIAWG

    /** OpenVPN runs in its own VpnService with the native openvpn core. */
    val isOpenVpn: Boolean get() = protocol == Protocol.OPENVPN

    /**
     * Stable server identity used by subscription refreshes: the same endpoint
     * and credentials update the existing profile instead of adding a duplicate.
     * Returns null for profiles without enough information to be identified.
     */
    fun identityKey(): String? {
        val host = address.trim().lowercase()
        if (host.isEmpty() || port !in 1..65535) return null
        val credential = when (protocol) {
            Protocol.VMESS, Protocol.VLESS -> fields["uuid"]
            Protocol.TROJAN, Protocol.SHADOWSOCKS -> fields["password"]
            Protocol.SOCKS, Protocol.HTTP, Protocol.HTTPS -> fields["username"]?.let { "$it:${fields["password"]}" }
            Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
                wgConfig.lineSequence()
                    .firstOrNull { it.trim().startsWith("PublicKey", ignoreCase = true) }
                    ?.substringAfter('=')?.trim()
            Protocol.OPENVPN ->
                ovpnConfig.lineSequence()
                    .firstOrNull { it.trim().startsWith("remote ", ignoreCase = true) }
                    ?.trim()?.lowercase()
        } ?: return null
        if (credential.isBlank()) return null
        return listOf(protocol.name.lowercase(), host, port.toString(), credential)
            .joinToString("|")
    }

    /** Short description line shown under the profile name in lists. */    fun summary(): String = when (protocol) {
        Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
            wgConfig.lineSequence().firstOrNull { it.startsWith("Endpoint") }
                ?.substringAfter('=')?.trim()?.ifEmpty { null }
                ?: protocol.displayName
        Protocol.OPENVPN -> {
            val remote = ovpnConfig.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("remote ", ignoreCase = true) }
                ?.split(Regex("\\s+"))
            if (remote != null && remote.size >= 3) {
                val proto = remote.getOrNull(3)?.lowercase()?.removeSuffix("4")?.removeSuffix("6")
                    ?.takeIf { it == "udp" || it == "tcp" }
                "${remote[1]}:${remote[2]}" + (proto?.let { " · $it" } ?: "")
            } else {
                protocol.displayName
            }
        }
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
        put("ovpnConfig", ovpnConfig)
        subscriptionId?.let { put("subscriptionId", it) }
        if (favorite) put("favorite", true)
        group?.takeIf { it.isNotEmpty() }?.let { put("group", it) }
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
            ovpnConfig = o.optString("ovpnConfig"),
            subscriptionId = o.optString("subscriptionId").ifEmpty { null },
            favorite = o.optBoolean("favorite", false),
            group = o.optString("group").ifEmpty { null },
        )
    }
}
