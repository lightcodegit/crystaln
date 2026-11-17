package com.crystaln.vpn.data

import android.util.Base64
import android.net.Uri

/**
 * Parses share links (v2ray URI schemes) into profiles.
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
        line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) ->
            parseHttpUrl(line)
        else -> null
    }

    private fun nameOf(fragment: String, fallback: String): String {
        val decoded = Uri.decode(fragment)
        return decoded.ifEmpty { fallback }
    }

    private fun parseVless(link: String): Profile? {
        return try {
            val uri = Uri.parse(link)
            val p = Profile(
                name = nameOf(uri.fragment ?: "", "VLESS"),
                protocol = Protocol.VLESS,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["uuid"] = uri.userInfo ?: return null
            applyCommonQuery(p, uri)
            uri.getQueryParameter("flow")?.takeIf { it.isNotEmpty() }?.let { p.fields["flow"] = it }
            p
        } catch (_: Exception) {
            null
        }
    }

    private fun parseTrojan(link: String): Profile? {
        return try {
            val uri = Uri.parse(link)
            val p = Profile(
                name = nameOf(uri.fragment ?: "", "Trojan"),
                protocol = Protocol.TROJAN,
                address = uri.host ?: return null,
                port = uri.port.takeIf { it > 0 } ?: 443,
            )
            p.fields["password"] = uri.userInfo ?: return null
            p.fields["tls"] = "true"
            applyCommonQuery(p, uri)
            p
        } catch (_: Exception) {
            null
        }
    }

    /**
     * host:port splitter that understands bare IPv6 in brackets,
     * e.g. "[2001:db8::1]:8388".
     */
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
                val decoded = String(Base64.decode(main.substringBefore('/'), Base64.DEFAULT)).trim()
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
                name = nameOf(frag, "SS"),
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

    /** v2rayN style: vmess://base64({"add","port","id","aid","net","type","host","path","tls","sni","ps",...}) */
    private fun parseVmess(link: String): Profile? {
        return try {
            val json = org.json.JSONObject(String(Base64.decode(link.removePrefix("vmess://"), Base64.DEFAULT)))
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
            val uri = Uri.parse(link)
            val p = Profile(
                name = nameOf(uri.fragment ?: "", protocol.displayName),
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
    private fun parseHttpUrl(link: String): Profile? {
        return try {
            val uri = Uri.parse(link)
            val path = uri.path ?: "/"
            // URLs with a real path are web pages, not proxies.
            if (uri.userInfo.isNullOrEmpty() && path.length > 1) return null
            val p = Profile(
                name = nameOf(uri.fragment ?: "", if (uri.scheme.equals("https", true)) "HTTPS" else "HTTP"),
                protocol = if (uri.scheme.equals("https", true)) Protocol.HTTPS else Protocol.HTTP,
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

    private fun applyCommonQuery(p: Profile, uri: Uri) {
        // v2rayN and other clients use "type" (newer) or "network" (older).
        (uri.getQueryParameter("type")?.takeIf { it.isNotEmpty() }
            ?: uri.getQueryParameter("network")?.takeIf { it.isNotEmpty() })?.let {
            p.fields["network"] = it
        }
        when (uri.getQueryParameter("security")) {
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
            uri.getQueryParameter(q)?.takeIf { it.isNotEmpty() }?.let { p.fields[key] = it }
        }
        when (uri.getQueryParameter("allowInsecure")) {
            "1", "true" -> p.fields["allowInsecure"] = "true"
        }
    }

    private fun decodeUserInfo(s: String): Pair<String, String>? {
        return try {
            val decoded = try {
                String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            } catch (_: IllegalArgumentException) {
                String(Base64.decode(s, Base64.DEFAULT))
            }.trim()
            val i = decoded.indexOf(':')
            if (i > 0) decoded.substring(0, i) to decoded.substring(i + 1) else null
        } catch (_: Exception) {
            null
        }
    }
}
