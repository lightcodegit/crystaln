package com.crystaln.vpn.data

import java.net.URLDecoder
import java.util.Base64

/**
 * Parses share links (v2ray URI schemes) into profiles. Desktop port of the
 * Android parser: java.net.URI + java.util.Base64 instead of the Android
 * classes, with a lenient raw-string fallback for links java.net.URI rejects.
 *
 * Supported: vless://, trojan://, ss:// (SIP002 and legacy), vmess:// (v2rayN
 * base64 JSON), socks5://, http(s):// proxies. Unrecognized lines are skipped;
 * [parse] returns null for anything it cannot handle.
 */
object ShareLinkParser {

    fun parseAll(text: String): List<Profile> =
        text.lines().mapNotNull { parse(it.trim()) }

    fun parse(line: String): Profile? = when {
        line.startsWith("vless://", ignoreCase = true) -> parseVless(line)
        line.startsWith("trojan://", ignoreCase = true) -> parseTrojan(line)
        line.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(line)
        line.startsWith("vmess://", ignoreCase = true) -> parseVmess(line)
        line.startsWith("socks://", ignoreCase = true) || line.startsWith("socks5://", ignoreCase = true) ->
            parseUserPass(line, Protocol.SOCKS)
        line.startsWith("hysteria2://", ignoreCase = true) || line.startsWith("hy2://", ignoreCase = true) ->
            parseHysteria2(line)
        line.startsWith("tuic://", ignoreCase = true) ->
            parseTuic(line)
        line.startsWith("anytls://", ignoreCase = true) ->
            parseAnytls(line)
        line.startsWith("hysteria://", ignoreCase = true) ->
            parseHysteria1(line)
        line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) ->
            parseHttpUrl(line)
        else -> null
    }

    // ── lenient URI helpers ──────────────────────────────────────────────

    private class ParsedUri(
        val host: String,
        val port: Int,
        val userInfo: String?,
        val path: String,
        val fragment: String?,
        val query: Map<String, String>,
    )

    /**
     * Splitter that tolerates the quirks of real-world share links (raw
     * unicode fragments, missing ports, spaces): scheme://[user@]host[:port]/path?q#f
     */
    private fun splitUri(line: String): ParsedUri? {
        val schemeEnd = line.indexOf("://")
        if (schemeEnd < 0) return null
        var rest = line.substring(schemeEnd + 3)
        val fragment = rest.indexOf('#').takeIf { it >= 0 }?.let {
            val f = rest.substring(it + 1)
            rest = rest.substring(0, it)
            URLDecoder.decode(f, Charsets.UTF_8)
        }
        val query = rest.indexOf('?').takeIf { it >= 0 }?.let {
            val q = rest.substring(it + 1)
            rest = rest.substring(0, it)
            parseQuery(q)
        } ?: emptyMap()
        val authority = rest.substringBefore('/')
        val path = rest.removePrefix(authority)
        val hasUserInfo = authority.indexOf('@') >= 0
        val rawUser = authority.substringBefore('@')
        // "@:host" (empty credentials) is malformed, not an anonymous user.
        val userInfo = rawUser.takeIf { hasUserInfo && it.isNotEmpty() }
        val hostPort = if (hasUserInfo) authority.substringAfter('@') else authority
        val host: String
        val port: Int
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close <= 0) return null
            host = hostPort.substring(1, close)
            port = hostPort.substring(close + 1).removePrefix(":").toIntOrNull() ?: 0
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon > 0) {
                host = hostPort.substring(0, colon)
                port = hostPort.substring(colon + 1).toIntOrNull() ?: 0
            } else {
                host = hostPort
                port = 0
            }
        }
        // Reject non-hosts: ":0", "host:", "a@b", stray colons without brackets.
        if (host.isEmpty() || host.contains('@') ||
            (host.contains(':') && !hostPort.startsWith("[")) ||
            port !in 0..65535
        ) {
            return null
        }
        return ParsedUri(host, port, userInfo, path, fragment, query)
    }    private fun parseQuery(q: String): Map<String, String> =
        q.split('&')
            .filter { it.isNotEmpty() }
            .associate {
                val i = it.indexOf('=')
                if (i < 0) URLDecoder.decode(it, Charsets.UTF_8) to ""
                else URLDecoder.decode(it.substring(0, i), Charsets.UTF_8) to
                    URLDecoder.decode(it.substring(i + 1), Charsets.UTF_8)
            }

    private fun nameOf(fragment: String?, fallback: String): String =
        fragment?.takeIf { it.isNotEmpty() } ?: fallback

    // ── schemes ──────────────────────────────────────────────────────────

    private fun parseVless(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "VLESS"),
                protocol = Protocol.VLESS,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["uuid"] = uri.userInfo ?: return null
            applyCommonQuery(p, uri.query)
            uri.query["flow"]?.takeIf { it.isNotEmpty() }?.let { p.fields["flow"] = it }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojan(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "Trojan"),
                protocol = Protocol.TROJAN,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["password"] = uri.userInfo ?: return null
            p.fields["tls"] = "true"
            applyCommonQuery(p, uri.query)
            p
        } catch (_: Exception) {
            null
        }
    }

    /**
     * hysteria2://password@host:port/?sni=…&insecure=1&obfs=salamander&obfs-password=…#name
     * ("hy2://" alias). Bandwidth hints: up/upmbps, down/downmbps.
     */
    private fun parseHysteria2(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "Hysteria2"),
                protocol = Protocol.HYSTERIA2,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["password"] = uri.userInfo ?: return null
            listOf(
                "sni" to "sni",
                "obfs" to "obfs",
                "obfs-password" to "obfsPassword",
                "obfsPassword" to "obfsPassword",
            ).forEach { (q, key) ->
                uri.query[q]?.takeIf { it.isNotEmpty() }?.let { p.fields[key] = it }
            }
            when (uri.query["insecure"]) {
                "1", "true" -> p.fields["allowInsecure"] = "true"
            }
            fun mbps(vararg keys: String): Int? = keys.firstNotNullOfOrNull { k ->
                uri.query[k]?.trim()?.removeSuffix("mbps")?.removeSuffix("Mbps")
                    ?.toIntOrNull()?.takeIf { it > 0 }
            }
            mbps("up", "upmbps", "up-mbps")?.let { p.fields["upMbps"] = it.toString() }
            mbps("down", "downmbps", "down-mbps")?.let { p.fields["downMbps"] = it.toString() }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(link: String): Profile? {
        return try {
            val body = link.removePrefix("ss://")
            val hash = body.indexOf('#')
            val frag = if (hash >= 0) body.substring(hash + 1) else ""
            val main = if (hash >= 0) body.substring(0, hash) else body

            val host: String
            val port: Int
            val cred: Pair<String, String>
            val at = main.lastIndexOf('@')
            if (at >= 0) {
                // SIP002: base64(method:password)@host:port
                cred = decodeUserInfo(main.substring(0, at)) ?: return null
                val (h, p) = splitHostPort(main.substring(at + 1)) ?: return null
                host = h
                port = p
            } else {
                // Legacy: base64(method:password@host:port)
                val decoded = String(Base64.getMimeDecoder().decode(main.substringBefore('/'))).trim()
                val atIdx = decoded.lastIndexOf('@')
                if (atIdx <= 0) return null
                val creds = decoded.substring(0, atIdx)
                val i = creds.indexOf(':')
                if (i <= 0) return null
                cred = creds.substring(0, i) to creds.substring(i + 1)
                val (h, p) = splitHostPort(decoded.substring(atIdx + 1)) ?: return null
                host = h
                port = p
            }
            Profile(
                name = nameOf(URLDecoder.decode(frag, Charsets.UTF_8), "SS"),
                protocol = Protocol.SHADOWSOCKS,
                address = host,
                port = port,
            ).apply {
                fields["method"] = cred.first
                fields["password"] = cred.second
            }
        } catch (_: Exception) {
            null
        }
    }

    /** host:port splitter that understands bare IPv6 in brackets. */
    private fun splitHostPort(raw: String): Pair<String, Int>? {
        val t = raw.substringBefore('/').substringBefore('?').trim()
        if (t.startsWith("[")) {
            val close = t.indexOf(']')
            if (close <= 0) return null
            val host = t.substring(1, close)
            val port = t.substring(close + 1).removePrefix(":").toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return host to port
        }
        val colon = t.lastIndexOf(':')
        if (colon <= 0) return null
        val port = t.substring(colon + 1).toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return t.substring(0, colon) to port
    }

    /** v2rayN style: vmess://base64({"add","port","id","aid","net",...}) */
    private fun parseVmess(link: String): Profile? {
        return try {
            val json = org.json.JSONObject(
                String(Base64.getMimeDecoder().decode(link.removePrefix("vmess://"))),
            )
            val p = Profile(
                name = json.optString("ps").ifEmpty { "VMess" },
                protocol = Protocol.VMESS,
                address = json.optString("add"),
                port = json.optInt("port"),
            )
            if (p.address.isEmpty() || p.port <= 0) return null
            p.fields["uuid"] = json.optString("id")
            p.fields["alterId"] = json.optInt("aid").toString()
            json.optString("scy").takeIf { it.isNotEmpty() }?.let { p.fields["security"] = it }
            json.optString("net").takeIf { it.isNotEmpty() }?.let { p.fields["network"] = it }
            json.optString("type").takeIf { it.isNotEmpty() && it != "none" }?.let { p.fields["headerType"] = it }
            json.optString("host").takeIf { it.isNotEmpty() }?.let { p.fields["host"] = it }
            json.optString("path").takeIf { it.isNotEmpty() }?.let { p.fields["path"] = it }
            if (json.optString("tls") == "tls") p.fields["tls"] = "true"
            json.optString("sni").takeIf { it.isNotEmpty() }?.let { p.fields["sni"] = it }
            json.optString("fp").takeIf { it.isNotEmpty() }?.let { p.fields["fingerprint"] = it }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseUserPass(link: String, protocol: Protocol): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, protocol.displayName),
                protocol = protocol,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 1080,
            )
            uri.userInfo?.let { info ->
                val i = info.indexOf(':')
                if (i > 0) {
                    p.fields["username"] = info.substring(0, i)
                    p.fields["password"] = info.substring(i + 1)
                }
            }
            p
        } catch (_: Exception) {
            null
        }
    }

    /** http(s)://user:pass@host:port#name imported as HTTP proxy profile. */
    private fun parseTuic(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "TUIC"),
                protocol = Protocol.TUIC,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            val info = uri.userInfo ?: return null
            val i = info.indexOf(':')
            if (i > 0) {
                p.fields["uuid"] = info.substring(0, i)
                p.fields["password"] = info.substring(i + 1)
            } else {
                p.fields["uuid"] = info
            }
            listOf(
                "sni" to "sni",
                "congestion_control" to "congestionControl",
                "udp_relay_mode" to "udpRelayMode",
            ).forEach { (q, key) ->
                uri.query[q]?.takeIf { it.isNotEmpty() }?.let { p.fields[key] = it }
            }
            when (uri.query["allow_insecure"] ?: uri.query["insecure"]) {
                "1", "true" -> p.fields["allowInsecure"] = "true"
            }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAnytls(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "AnyTLS"),
                protocol = Protocol.ANYTLS,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["password"] = uri.userInfo ?: return null
            uri.query["sni"]?.takeIf { it.isNotEmpty() }?.let { p.fields["sni"] = it }
            when (uri.query["insecure"] ?: uri.query["allow_insecure"]) {
                "1", "true" -> p.fields["allowInsecure"] = "true"
            }
            p
        } catch (_: Exception) { null }
    }

    private fun parseHysteria1(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            val p = Profile(
                name = nameOf(uri.fragment, "Hysteria"),
                protocol = Protocol.HYSTERIA1,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            (uri.query["auth"])?.takeIf { it.isNotEmpty() }?.let { p.fields["password"] = it }
            (uri.query["peer"])?.takeIf { it.isNotEmpty() }?.let { p.fields["sni"] = it }
            (uri.query["upmbps"] ?: uri.query["up"])?.toIntOrNull()?.takeIf { it > 0 }?.let { p.fields["upMbps"] = it.toString() }
            (uri.query["downmbps"] ?: uri.query["down"])?.toIntOrNull()?.takeIf { it > 0 }?.let { p.fields["downMbps"] = it.toString() }
            when (uri.query["insecure"] ?: uri.query["allowInsecure"]) {
                "1", "true" -> p.fields["allowInsecure"] = "true"
            }
            if (p.fields["password"].isNullOrEmpty()) return null
            p
        } catch (_: Exception) { null }
    }

    private fun parseHttpUrl(link: String): Profile? {
        return try {
            val uri = splitUri(link) ?: return null
            // URLs with a real path are web pages, not proxies.
            if (uri.userInfo.isNullOrEmpty() && uri.path.length > 1) return null
            // The splitter drops the scheme; re-check the raw link.
            val https = link.startsWith("https://", ignoreCase = true)
            val p = Profile(
                name = nameOf(uri.fragment, if (https) "HTTPS" else "HTTP"),
                protocol = if (https) Protocol.HTTPS else Protocol.HTTP,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 8080,
            )
            uri.userInfo?.let { info ->
                val i = info.indexOf(':')
                if (i > 0) {
                    p.fields["username"] = info.substring(0, i)
                    p.fields["password"] = info.substring(i + 1)
                }
            }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun applyCommonQuery(p: Profile, query: Map<String, String>) {
        // v2rayN and other clients use "type" (newer) or "network" (older).
        (query["type"]?.takeIf { it.isNotEmpty() } ?: query["network"]?.takeIf { it.isNotEmpty() })?.let {
            p.fields["network"] = it
        }
        when (query["security"]) {
            "tls" -> p.fields["tls"] = "true"
            "reality" -> p.fields["reality"] = "true"
        }
        listOf(
            "sni" to "sni",
            "host" to "host",
            "path" to "path",
            "serviceName" to "serviceName",
            "fp" to "fingerprint",
            "pbk" to "publicKey",
            "sid" to "shortId",
            "spx" to "spiderX",
        ).forEach { (q, key) ->
            query[q]?.takeIf { it.isNotEmpty() }?.let { p.fields[key] = it }
        }
        when (query["allowInsecure"]) {
            "1", "true" -> p.fields["allowInsecure"] = "true"
        }
    }

    private fun decodeUserInfo(s: String): Pair<String, String>? {
        return try {
            val decoded = try {
                String(Base64.getUrlDecoder().decode(stripPadding(s)))
            } catch (_: IllegalArgumentException) {
                String(Base64.getMimeDecoder().decode(s))
            }.trim()
            val i = decoded.indexOf(':')
            if (i > 0) decoded.substring(0, i) to decoded.substring(i + 1) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun stripPadding(s: String): String = s.trimEnd('=')
}
