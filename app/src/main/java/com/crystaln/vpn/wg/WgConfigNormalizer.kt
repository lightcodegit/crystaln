package com.crystaln.vpn.wg

import com.crystaln.vpn.data.AwgParams

/**
 * Normalizes wg-quick / AmneziaWG config text into a form the AmneziaWG core
 * config parser accepts (`org.amnezia.awg.config.Config`):
 *
 *  - the dedicated `[AmneziaWG]` section used by some exporters is merged into
 *    `[Interface]` and removed from the output — the parser throws
 *    UNKNOWN_SECTION for anything but `[Interface]`/`[Peer]`;
 *  - junk/obfuscation params stored in the profile (Jc, Jmin, Jmax, S1, S2,
 *    H1..H4) are injected for keys not already present in the config, so
 *    explicit config keys always win.
 */
object WgConfigNormalizer {

    /** Junk/obfuscation keys understood by the AmneziaWG interface parser. */
    private val JUNK_KEYS = setOf(
        "JC", "JMIN", "JMAX", "S1", "S2", "S3", "S4",
        "H1", "H2", "H3", "H4",
        "I1", "I2", "I3", "I4", "I5",
        "HEADERPROTECTIONKEY", "CONTENTPADDINGADDITION",
    )

    fun normalize(config: String, fieldParams: AwgParams = AwgParams()): String {
        val lines = config.lines()

        // Pass 1: locate sections; collect junk keys present in [Interface]
        // and the body of the [AmneziaWG] section.
        var section = ""
        val interfaceJunkKeys = mutableSetOf<String>()
        val awgSectionLines = mutableListOf<String>()
        var hasInterfaceSection = false
        lines.forEach { line ->
            val t = line.trim()
            if (t.startsWith("[")) {
                val name = sectionName(t)
                if (name == "INTERFACE") hasInterfaceSection = true
                section = name
                return@forEach
            }
            if (t.isEmpty() || t.startsWith("#") || !t.contains('=')) return@forEach
            val key = t.substringBefore('=').trim().uppercase()
            when (section) {
                "INTERFACE" -> if (key in JUNK_KEYS) interfaceJunkKeys += key
                "AMNEZIAWG" -> if (key in JUNK_KEYS) awgSectionLines += t
            }
        }

        // Lines to inject right after the [Interface] header: keys already
        // present in [Interface] are not duplicated.
        val inject = mutableListOf<String>()
        val injectedKeys = mutableSetOf<String>()
        awgSectionLines.forEach { line ->
            val key = keyOf(line)
            if (key !in injectedKeys) {
                injectedKeys += key
                inject += line
            }
        }
        AwgParams.iniLinesOf(fieldParams).takeIf { it.isNotBlank() }?.lineSequence()?.forEach { line ->
            val key = keyOf(line)
            if (key !in interfaceJunkKeys && key !in injectedKeys) {
                injectedKeys += key
                inject += line
            }
        }

        // Pass 2: emit the config without the [AmneziaWG] section, with the
        // collected params injected right after [Interface].
        val sb = StringBuilder()
        section = ""
        var injected = false
        lines.forEach { line ->
            val t = line.trim()
            if (t.startsWith("[")) {
                section = sectionName(t)
                if (section == "AMNEZIAWG") return@forEach // section dropped
            } else if (section == "AMNEZIAWG") {
                return@forEach // section body dropped
            }
            sb.appendLine(line)
            if (section == "INTERFACE" && !injected) {
                inject.forEach { sb.appendLine(it) }
                injected = true
            }
        }

        // Config without any [Interface] header but with params to inject:
        // emit a minimal section so the parser has somewhere to put them.
        if (!hasInterfaceSection && inject.isNotEmpty()) {
            val header = StringBuilder("[Interface]").appendLine()
            inject.forEach { header.appendLine(it) }
            return header.toString() + sb.toString()
        }
        return sb.toString()
    }

    private fun sectionName(header: String): String =
        header.trim().trim('[', ']').uppercase().filter { it.isLetterOrDigit() }

    private fun keyOf(line: String): String = line.substringBefore('=').trim().uppercase()
}
