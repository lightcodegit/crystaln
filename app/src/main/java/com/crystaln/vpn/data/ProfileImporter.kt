package com.crystaln.vpn.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal profile import from text.
 *
 * Handles, in order:
 * - JSON export format {"profiles":[...]} produced by CrystalN export;
 * - wg-quick / AmneziaWG config text ([Interface] / [Peer] / [AmneziaWG]);
 * - OpenVPN .ovpn config text (remote + dev tun + inline CA);
 * - share-link lists (vless://, trojan://, ss://, vmess://, socks5://, http(s)://).
 */
object ProfileImporter {

    fun parseText(text: String): List<Profile> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // JSON export?
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val fromJson = parseJsonExport(trimmed)
            if (fromJson.isNotEmpty()) return fromJson
        }

        // WireGuard / AmneziaWG config?
        if (trimmed.contains("[Interface]", ignoreCase = true) ||
            trimmed.contains("[AmneziaWG]", ignoreCase = true)
        ) {
            parseWireGuardFamily(trimmed)?.let { return listOf(it) }
        }

        // OpenVPN .ovpn config?
        if (isOpenVpnConfig(trimmed)) {
            parseOpenVpn(trimmed)?.let { return listOf(it) }
        }

        return ShareLinkParser.parseAll(trimmed)
    }

    /**
     * Heuristic .ovpn detection: needs a `remote` line plus at least one other
     * OpenVPN-specific directive. Share links and wg configs never match this.
     */
    private fun isOpenVpnConfig(text: String): Boolean {
        val lines = text.lines().map { it.trim().lowercase() }
        val hasRemote = lines.any { it.startsWith("remote ") }
        if (!hasRemote) return false
        val markers = setOf("client", "tls-client", "dev tun", "proto ", "verb ", "cipher ", "<ca>", "-----begin cert")
        return lines.any { line -> markers.any { line.contains(it) } }
    }

    private fun parseOpenVpn(config: String): Profile? {
        return try {
            val remote = config.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("remote ", ignoreCase = true) }
                ?.split(Regex("\\s+"))
                ?: return null
            if (remote.size < 2) return null
            val host = remote[1]
            val port = remote.getOrNull(2)?.toIntOrNull() ?: 1194
            Profile(
                name = "OpenVPN $host:$port",
                protocol = Protocol.OPENVPN,
            ).apply {
                address = host
                this.port = port
                ovpnConfig = config.trim()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJsonExport(text: String): List<Profile> {
        return try {
            val root = JSONObject(text)
            val arr: JSONArray = when {
                root.has("profiles") -> root.getJSONArray("profiles")
                else -> JSONArray()
            }
            (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            // Possibly a bare array.
            try {
                val arr = JSONArray(text)
                (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun parseWireGuardFamily(config: String): Profile? {
        return try {
            val lines = config.lines()
            var inInterface = false
            var endpoint = ""
            var hasAwgSection = false
            val awgKeys = setOf("JC", "JMIN", "JMAX", "S1", "S2", "H1", "H2", "H3", "H4")
            val awgAttrs = mutableMapOf<String, String>()

            lines.forEach { line ->
                val t = line.trim()
                when {
                    t.equals("[Interface]", ignoreCase = true) -> inInterface = true
                    t.equals("[AmneziaWG]", ignoreCase = true) -> {
                        inInterface = false
                        hasAwgSection = true
                    }

                    t.startsWith("[") -> inInterface = false
                    t.isEmpty() || t.startsWith("#") -> Unit
                    else -> {
                        val key = t.substringBefore('=').trim().uppercase()
                        val value = t.substringAfter('=', "").trim()
                        when {
                            key == "ENDPOINT" -> endpoint = value
                            inInterface && key in awgKeys -> awgAttrs[key.lowercase()] = value
                        }
                    }
                }
            }

            if (endpoint.isEmpty()) return null
            val host = endpoint.substringBeforeLast(':').trimStart('[').trimEnd(']')
            val port = endpoint.substringAfterLast(':').toIntOrNull() ?: return null
            if (host.isEmpty() || port <= 0) return null

            // A profile counts as AmneziaWG when obfuscation attrs or a dedicated
            // [AmneziaWG] section are present.
            val isAwg = hasAwgSection || awgAttrs.isNotEmpty()
            val params = if (isAwg) AwgParams(
                jc = awgAttrs["jc"]?.toIntOrNull() ?: 0,
                jmin = awgAttrs["jmin"]?.toIntOrNull() ?: 0,
                jmax = awgAttrs["jmax"]?.toIntOrNull() ?: 0,
                s1 = awgAttrs["s1"]?.toIntOrNull() ?: 0,
                s2 = awgAttrs["s2"]?.toIntOrNull() ?: 0,
                h1 = awgAttrs["h1"]?.toLongOrNull() ?: 0L,
                h2 = awgAttrs["h2"]?.toLongOrNull() ?: 0L,
                h3 = awgAttrs["h3"]?.toLongOrNull() ?: 0L,
                h4 = awgAttrs["h4"]?.toLongOrNull() ?: 0L,
            ) else AwgParams()

            Profile(
                name = "AWG $host:$port".takeIf { isAwg } ?: "WG $host:$port",
                protocol = if (isAwg) Protocol.AMNEZIAWG else Protocol.WIREGUARD,
            ).apply {
                this.address = host
                this.port = port
                wgConfig = config.trim()
                if (isAwg) {
                    // Store params in fields; AwgTunnel injects only missing keys,
                    // so params already present in wgConfig are not duplicated.
                    fields["jc"] = params.jc.toString()
                    fields["jmin"] = params.jmin.toString()
                    fields["jmax"] = params.jmax.toString()
                    fields["s1"] = params.s1.toString()
                    fields["s2"] = params.s2.toString()
                    fields["h1"] = params.h1.toString()
                    fields["h2"] = params.h2.toString()
                    fields["h3"] = params.h3.toString()
                    fields["h4"] = params.h4.toString()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun exportJson(profiles: List<Profile>, selectedId: String?): String = JSONObject()
        .put("format", "crystaln/1")
        .put("selected", selectedId ?: "")
        .put("profiles", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
        .toString(2)
}
