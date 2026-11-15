package com.crystaln.vpn.desktop.net

/**
 * Converts an imported wg-quick config (possibly with an [AmneziaWG] section)
 * to the subset understood by `awg setconf` from amneziawg-tools 3.x.
 *
 * awg setconf knows only device and peer keys. Address/DNS/MTU/Table and hook
 * commands are returned separately and applied with `ip` by TunnelManager.
 * AmneziaWG parameters are deliberately kept, including S3/S4 and I1..I5,
 * which are not exposed by our editor but may be present in an imported file.
 */
object WgSetconf {

    data class Result(
        val setconf: String,
        val addresses: List<String>,
        val dnsServers: List<String>,
        val mtu: Int,
        val routes: List<String>,
        val defaultRoute: Boolean,
    )

    private val canonicalKeys = mapOf(
        "privatekey" to "PrivateKey",
        "listenport" to "ListenPort",
        "fwmark" to "FwMark",
        "jc" to "Jc",
        "jmin" to "Jmin",
        "jmax" to "Jmax",
        "headerprotectionkey" to "HeaderProtectionKey",
        "contentpaddingaddition" to "ContentPaddingAddition",
        "publickey" to "PublicKey",
        "presharedkey" to "PresharedKey",
        "allowedips" to "AllowedIPs",
        "endpoint" to "Endpoint",
        "persistentkeepalive" to "PersistentKeepalive",
    ) + (1..4).associate { i -> "s$i" to "S$i" } +
        (1..4).associate { i -> "h$i" to "H$i" } +
        (1..5).associate { i -> "i$i" to "I$i" }

    private val junkKeys = setOf("jc", "jmin", "jmax") +
        (1..4).map { "s$it" } + (1..4).map { "h$it" } + (1..5).map { "i$it" }
    private val deviceKeys = setOf(
        "privatekey", "listenport", "fwmark", "headerprotectionkey", "contentpaddingaddition",
    ) + junkKeys
    private val peerKeys = setOf(
        "publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive",
    )

    /** [fields] are the editor junk parameters; non-default values win over the imported config. */
    fun build(wgConfig: String, fields: Map<String, String> = emptyMap()): Result {
        val device = LinkedHashMap<String, String>()
        val peers = mutableListOf<LinkedHashMap<String, String>>()
        var peer: LinkedHashMap<String, String>? = null
        var section = ""
        val addresses = LinkedHashSet<String>()
        val dns = LinkedHashSet<String>()
        var mtu: Int? = null

        wgConfig.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removeSurrounding("[", "]").trim().lowercase()
                peer = if (section == "peer") {
                    LinkedHashMap<String, String>().also {
                        peers.add(it)
                    }
                } else {
                    null
                }
                return@forEach
            }
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when (section) {
                "interface" -> when (key) {
                    "address" -> addresses.addAll(splitList(value))
                    "dns" -> dns.addAll(splitList(value))
                    "mtu" -> if (mtu == null) mtu = value.toIntOrNull()?.takeIf { it > 0 }
                    in deviceKeys -> device[key] = value
                    // wg-quick-only keys (Table, hooks, SaveConfig) are omitted.
                    else -> Unit
                }
                "amneziawg" -> if (key in junkKeys) device[key] = value
                "peer" -> if (key in peerKeys) peer?.set(key, value)
            }
        }

        fields.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim().lowercase()
            val value = rawValue.trim()
            if (key in junkKeys && isSetJunkValue(value)) device[key] = value
        }

        val routes = peers.asSequence()
            .flatMap { splitList(it["allowedips"].orEmpty()) }
            .distinct()
            .toList()
        val out = StringBuilder("[Interface]\n")
        deviceKeys.forEach { key ->
            device[key]?.let { out.appendLine("${canonicalKeys.getValue(key)} = $it") }
        }
        peers.forEach { values ->
            out.appendLine()
            out.appendLine("[Peer]")
            peerKeys.forEach { key ->
                values[key]?.let { out.appendLine("${canonicalKeys.getValue(key)} = $it") }
            }
        }
        return Result(
            setconf = out.toString(),
            addresses = addresses.toList(),
            dnsServers = dns.toList(),
            mtu = mtu ?: 1420,
            routes = routes,
            defaultRoute = "0.0.0.0/0" in routes || "::/0" in routes,
        )
    }

    private fun splitList(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun isSetJunkValue(value: String): Boolean {
        val range = Regex("""\d+-\d+""")
        return range.matches(value) || value.toLongOrNull()?.let { it > 0 } == true
    }
}
