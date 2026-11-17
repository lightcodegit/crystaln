package com.crystaln.vpn.data

import java.net.URLEncoder
import org.json.JSONObject
import java.util.Base64

/**
 * Inverse of [ShareLinkParser]: turns a profile back into a shareable text —
 * a v2ray-style share link for the Xray family, the raw wg-quick config for
 * WireGuard/AmneziaWG and the .ovpn text for OpenVPN.
 *
 * Desktop port of the Android file: java.net.URLEncoder instead of
 * android.net.Uri.
 *
 * Returns null when the profile has nothing sensible to share (no address,
 * no credentials) rather than producing a broken link.
 */
object ProfileShareLink {

    fun shareText(p: Profile): String? = when (p.protocol) {
        Protocol.VLESS -> shareVless(p)
        Protocol.TROJAN -> shareTrojan(p)
        Protocol.VMESS -> shareVmess(p)
        Protocol.SHADOWSOCKS -> shareShadowsocks(p)
        Protocol.SOCKS -> shareUserPass(p, "socks")
        Protocol.HTTP -> shareUserPass(p, "http")
        Protocol.HTTPS -> shareUserPass(p, "https")
        Protocol.WIREGUARD, Protocol.AMNEZIAWG -> p.wgConfig.ifEmpty { null }
        Protocol.OPENVPN -> p.ovpnConfig.ifEmpty { null }
        Protocol.HYSTERIA2 -> shareHysteria2(p)
    }

    /** hysteria2://password@host:port?params#name */
    private fun shareHysteria2(p: Profile): String? {
        val password = p.fields["password"]?.takeIf { it.isNotEmpty() } ?: return null
        if (p.address.isEmpty() || p.port <= 0) return null
        val q = mutableListOf<String>()
        p.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { q += "sni=${enc(it)}" }
        if (p.fields["allowInsecure"] == "true") q += "insecure=1"
        p.fields["obfs"]?.takeIf { it.isNotEmpty() }?.let { q += "obfs=${enc(it)}" }
        p.fields["obfsPassword"]?.takeIf { it.isNotEmpty() }?.let { q += "obfs-password=${enc(it)}" }
        p.fields["upMbps"]?.takeIf { it.isNotEmpty() }?.let { q += "upmbps=$it" }
        p.fields["downMbps"]?.takeIf { it.isNotEmpty() }?.let { q += "downmbps=$it" }
        val query = if (q.isEmpty()) "" else "?" + q.joinToString("&")
        return "hysteria2://${enc(password)}@${hostPort(p)}$query#${fragment(p)}"
    }

    /** Name goes into the URI fragment, URL-encoded. */
    private fun fragment(p: Profile): String =
        enc(p.name.ifEmpty { p.protocol.displayName })

    /** Like android.net.Uri.encode: keeps unreserved chars, encodes everything else. */
    private fun enc(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name())
            .replace("+", "%20")

    private fun commonQuery(p: Profile): String {
        val q = mutableListOf<String>()
        val network = p.fields["network"]?.takeIf { it.isNotEmpty() } ?: "tcp"
        if (network != "tcp") q += "type=${enc(network)}"
        val security = when {
            p.fields["reality"] == "true" -> "reality"
            p.fields["tls"] == "true" -> "tls"
            else -> null
        }
        security?.let { q += "security=$it" }
        listOf(
            "sni" to "sni",
            "host" to "host",
            "path" to "path",
            "serviceName" to "serviceName",
            "fingerprint" to "fp",
            "publicKey" to "pbk",
            "shortId" to "sid",
            "spiderX" to "spx",
        ).forEach { (key, query) ->
            p.fields[key]?.takeIf { it.isNotEmpty() }?.let { q += "$query=${enc(it)}" }
        }
        if (p.fields["allowInsecure"] == "true") q += "allowInsecure=1"
        return if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }

    private fun shareVless(p: Profile): String? {
        val uuid = p.fields["uuid"]?.takeIf { it.isNotEmpty() } ?: return null
        if (p.address.isEmpty() || p.port <= 0) return null
        val flow = p.fields["flow"]?.takeIf { it.isNotEmpty() }?.let { "&flow=${enc(it)}" } ?: ""
        return "vless://$uuid@${hostPort(p)}${commonQuery(p)}$flow#${fragment(p)}"
    }

    private fun shareTrojan(p: Profile): String? {
        val password = p.fields["password"]?.takeIf { it.isNotEmpty() } ?: return null
        if (p.address.isEmpty() || p.port <= 0) return null
        return "trojan://${enc(password)}@${hostPort(p)}${commonQuery(p)}#${fragment(p)}"
    }

    /** v2rayN-style base64 JSON. */
    private fun shareVmess(p: Profile): String? {
        val uuid = p.fields["uuid"]?.takeIf { it.isNotEmpty() } ?: return null
        if (p.address.isEmpty() || p.port <= 0) return null
        val json = JSONObject().apply {
            put("v", "2")
            put("ps", p.name)
            put("add", p.address)
            put("port", p.port)
            put("id", uuid)
            put("aid", (p.fields["alterId"] ?: "0").toIntOrNull() ?: 0)
            put("scy", p.fields["security"] ?: "auto")
            put("net", p.fields["network"]?.takeIf { it.isNotEmpty() } ?: "tcp")
            put("type", p.fields["headerType"] ?: "none")
            put("host", p.fields["host"] ?: "")
            put("path", p.fields["path"] ?: "")
            put("tls", if (p.fields["tls"] == "true") "tls" else "")
            put("sni", p.fields["sni"] ?: "")
            put("fp", p.fields["fingerprint"] ?: "")
        }
        val b64 = Base64.getEncoder().encodeToString(json.toString().toByteArray())
        return "vmess://$b64"
    }

    /** SIP002: base64(method:password)@host:port. */
    private fun shareShadowsocks(p: Profile): String? {
        val method = p.fields["method"]?.takeIf { it.isNotEmpty() } ?: return null
        val password = p.fields["password"]?.takeIf { it.isNotEmpty() } ?: return null
        if (p.address.isEmpty() || p.port <= 0) return null
        val userInfo = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$method:$password".toByteArray())
        return "ss://$userInfo@${hostPort(p)}#${fragment(p)}"
    }

    private fun shareUserPass(p: Profile, scheme: String): String? {
        if (p.address.isEmpty() || p.port <= 0) return null
        val user = p.fields["username"]
        val pass = p.fields["password"]
        val auth = if (!user.isNullOrEmpty()) {
            "${enc(user)}:${enc(pass ?: "")}@"
        } else ""
        return "$scheme://$auth${hostPort(p)}#${fragment(p)}"
    }

    /** Bare IPv6 hosts are bracketed so the port is unambiguous. */
    private fun hostPort(p: Profile): String {
        val host = p.address
        return if (host.contains(":")) "[$host]:${p.port}" else "$host:${p.port}"
    }
}
