package com.crystaln.vpn.ui

import com.crystaln.vpn.data.Profile

/**
 * Privacy mask for profile names: "🇩🇪 #4". The country is guessed from the
 * server hostname's ccTLD (or a flag emoji already present in the name);
 * the number is the profile's 1-based position in the list. Real names are
 * never modified — only what the UI displays.
 */
object ServerAlias {

    /** ccTLD → ISO country code for the flags we can reasonably guess. */
    private val ccTld = mapOf(
        "ae" to "AE", "am" to "AM", "ar" to "AR", "at" to "AT", "au" to "AU",
        "az" to "AZ", "ba" to "BA", "be" to "BE", "bg" to "BG", "br" to "BR",
        "by" to "BY", "ca" to "CA", "ch" to "CH", "cl" to "CL", "cn" to "CN",
        "co" to "CO", "cz" to "CZ", "de" to "DE", "dk" to "DK", "ee" to "EE",
        "es" to "ES", "fi" to "FI", "fr" to "FR", "ge" to "GE", "gr" to "GR",
        "hk" to "HK", "hr" to "HR", "hu" to "HU", "id" to "ID", "ie" to "IE",
        "il" to "IL", "in" to "IN", "is" to "IS", "it" to "IT", "jp" to "JP",
        "kz" to "KZ", "lt" to "LT", "lu" to "LU", "lv" to "LV", "md" to "MD",
        "mx" to "MX", "my" to "MY", "nl" to "NL", "no" to "NO", "nz" to "NZ",
        "ph" to "PH", "pl" to "PL", "pt" to "PT", "ro" to "RO", "rs" to "RS",
        "ru" to "RU", "se" to "SE", "sg" to "SG", "si" to "SI", "sk" to "SK",
        "th" to "TH", "tr" to "TR", "tw" to "TW", "ua" to "UA", "uk" to "GB",
        "us" to "US", "vn" to "VN", "za" to "ZA",
    )

    /** Builds a flag emoji out of a two-letter ISO code. */
    private fun flag(iso2: String): String {
        if (iso2.length != 2) return "🌐"
        return StringBuilder().apply {
            appendCodePoint(0x1F1E6 + (iso2[0] - 'A'))
            appendCodePoint(0x1F1E6 + (iso2[1] - 'A'))
        }.toString()
    }

    /** Flag emoji guessed for the profile; 🌐 when nothing matches. */
    fun flagFor(p: Profile): String {
        // 1) The name may already carry a flag emoji — reuse it.
        nameFlag(p.name)?.let { return it }
        // 2) The hostname's ccTLD.
        val host = p.address.substringBefore(':').substringBefore('/').lowercase()
        if (host.contains('.')) {
            ccTld[host.substringAfterLast('.')]?.let { return flag(it) }
        }
        return "🌐"
    }

    /** First flag emoji (regional-indicator pair) found in the text. */
    private fun nameFlag(text: String): String? {
        val cps = text.codePoints().toArray()
        for (i in 0 until cps.size - 1) {
            if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
                return StringBuilder().apply {
                    appendCodePoint(cps[i])
                    appendCodePoint(cps[i + 1])
                }.toString()
            }
        }
        return null
    }

    /** Masked display name: "🇩🇪 #4" for the 4th profile in the list. */
    fun label(p: Profile, index: Int): String = "${flagFor(p)} #${index + 1}"
}
