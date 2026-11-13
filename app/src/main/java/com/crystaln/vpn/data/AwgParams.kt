package com.crystaln.vpn.data

/**
 * AmneziaWG junk-packet parameters (obfuscation). Non-default values require
 * an AmneziaWG-aware server; when all are defaults the profile behaves like
 * plain WireGuard and can run on the standard wireguard-go backend.
 */
data class AwgParams(
    val jc: Int = 0,
    val jmin: Int = 0,
    val jmax: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val h1: Long = 0L,
    val h2: Long = 0L,
    val h3: Long = 0L,
    val h4: Long = 0L,
) {
    /** True when no obfuscation is configured: plain WireGuard semantics. */
    val isDefault: Boolean
        get() = jc == 0 && jmin == 0 && jmax == 0 && s1 == 0 && s2 == 0 &&
            h1 == 0L && h2 == 0L && h3 == 0L && h4 == 0L

    /** Serialized as an ini section appended to wg-quick style configs. */
    fun toIniSection(): String {
        if (isDefault) return ""
        return buildString {
            appendLine()
            appendLine("[AmneziaWG]")
            appendLine("Jc = $jc")
            appendLine("Jmin = $jmin")
            appendLine("Jmax = $jmax")
            appendLine("S1 = $s1")
            appendLine("S2 = $s2")
            appendLine("H1 = $h1")
            appendLine("H2 = $h2")
            appendLine("H3 = $h3")
            appendLine("H4 = $h4")
        }.trimEnd('\n')
    }

    companion object {
        private val KEYS = setOf("JC", "JMIN", "JMAX", "S1", "S2", "H1", "H2", "H3", "H4")

        /** Ini lines for the [Interface] section, only for non-default params. */
        fun iniLinesOf(p: AwgParams): String = if (p.isDefault) "" else buildString {
            appendLine("Jc = ${p.jc}")
            appendLine("Jmin = ${p.jmin}")
            appendLine("Jmax = ${p.jmax}")
            appendLine("S1 = ${p.s1}")
            appendLine("S2 = ${p.s2}")
            appendLine("H1 = ${p.h1}")
            appendLine("H2 = ${p.h2}")
            appendLine("H3 = ${p.h3}")
            appendLine("H4 = ${p.h4}")
        }.trimEnd('\n')

        /** Extracts [AmneziaWG] section params from a config and returns them plus the stripped config. */
        fun extract(config: String): Pair<AwgParams, String> {
            var jc = 0; var jmin = 0; var jmax = 0; var s1 = 0; var s2 = 0
            var h1 = 0L; var h2 = 0L; var h3 = 0L; var h4 = 0L
            var inSection = false
            val kept = StringBuilder()
            config.lines().forEach { line ->
                val t = line.trim()
                when {
                    t.equals("[AmneziaWG]", ignoreCase = true) -> inSection = true
                    t.startsWith("[") -> {
                        inSection = false
                        kept.appendLine(line)
                    }

                    inSection -> {
                        val key = t.substringBefore('=').trim().uppercase()
                        val value = t.substringAfter('=', "").trim()
                        if (key in KEYS && value.isNotEmpty()) {
                            when (key) {
                                "JC" -> jc = value.toIntOrNull() ?: 0
                                "JMIN" -> jmin = value.toIntOrNull() ?: 0
                                "JMAX" -> jmax = value.toIntOrNull() ?: 0
                                "S1" -> s1 = value.toIntOrNull() ?: 0
                                "S2" -> s2 = value.toIntOrNull() ?: 0
                                "H1" -> h1 = value.toLongOrNull() ?: 0L
                                "H2" -> h2 = value.toLongOrNull() ?: 0L
                                "H3" -> h3 = value.toLongOrNull() ?: 0L
                                "H4" -> h4 = value.toLongOrNull() ?: 0L
                            }
                        }
                        // Section body is consumed: params live in Profile.fields.
                    }

                    else -> kept.appendLine(line)
                }
            }
            return AwgParams(jc, jmin, jmax, s1, s2, h1, h2, h3, h4) to
                kept.toString().trimEnd('\n')
        }

        /** Reads params from profile.fields (as stored by the editor/import). */
        fun fromFields(fields: Map<String, String>): AwgParams = AwgParams(
            jc = fields["jc"]?.toIntOrNull() ?: 0,
            jmin = fields["jmin"]?.toIntOrNull() ?: 0,
            jmax = fields["jmax"]?.toIntOrNull() ?: 0,
            s1 = fields["s1"]?.toIntOrNull() ?: 0,
            s2 = fields["s2"]?.toIntOrNull() ?: 0,
            h1 = fields["h1"]?.toLongOrNull() ?: 0L,
            h2 = fields["h2"]?.toLongOrNull() ?: 0L,
            h3 = fields["h3"]?.toLongOrNull() ?: 0L,
            h4 = fields["h4"]?.toLongOrNull() ?: 0L,
        )
    }
}
