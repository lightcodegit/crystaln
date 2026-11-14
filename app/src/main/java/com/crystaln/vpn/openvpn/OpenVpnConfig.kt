package com.crystaln.vpn.openvpn

import com.crystaln.vpn.data.Profile
import java.io.File

/**
 * Prepares a user's .ovpn text for the OpenVPN core.
 *
 * OpenVPN is built for TARGET_ANDROID: it asks the app to build the tun
 * through the management interface (`IFCONFIG`, `ROUTE`, `DNSSERVER`,
 * `OPENTUN` NEED-OK queries, see doc/android.txt in the openvpn tree), so
 * the runtime config must carry the `management <socket> unix` /
 * `management-client` / `management-hold` plumbing — appended here, never
 * passed as CLI arguments (management directives are not accepted as
 * options after the config file on all versions and the config file is the
 * single source of truth ics-openvpn uses too).
 *
 * Directives that can't work on Android (script hooks, external helpers,
 * duplicate management/status settings) are stripped; when the user filled
 * in login/password an `auth-user-pass <file>` line is added.
 */
object OpenVpnConfig {

    /** Directives removed from the runtime config, matched by first word. */
    private val stripDirectives = setOf(
        "auth-user-pass",
        "auth-user-pass-verify",
        "config",
        "dev-node",
        "down",
        "iproute",
        "learn-address",
        "management",
        "management-client",
        "management-client-auth",
        "management-client-group",
        "management-client-user",
        "management-external-cert",
        "management-external-key",
        "management-forget-disconnect",
        "management-hold",
        "management-log-cache",
        "management-query-passwords",
        "management-query-proxy",
        "management-query-remote",
        "management-signal",
        "management-up-down",
        "nice",
        "plugin",
        "route-up",
        "script-security",
        "status",
        "status-version",
        "tls-client-verify",
        "up",
        "verify-client-cert",
        "writepid",
    )

    /** Result of preparing the config for a start attempt. */
    data class Prepared(
        /** Sanitized config text to write into config.ovpn. */
        val config: String,
        /**
         * True when the original config asks for auth-user-pass: without
         * credentials from the profile editor the server will reject us.
         */
        val needsAuth: Boolean,
    )

    /**
     * Builds the runtime config text.
     *
     * @param mgmtSocketPath absolute path of the management unix socket the
     *   service listens on.
     * @param authFile credentials file, or null when the user left the
     *   login/password empty.
     * @return null when the text is not a usable client config.
     */
    fun prepare(profile: Profile, mgmtSocketPath: String, authFile: File?): Prepared? {
        val raw = profile.ovpnConfig
        if (raw.isBlank()) return null

        val out = mutableListOf<String>()
        var hasDev = false
        var hasVerb = false
        var hasClient = false
        var needsAuth = false

        raw.lines().forEach { line0 ->
            val line = line0.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                out.add(line)
                return@forEach
            }
            val key = line.substringBefore(' ').lowercase()
            when {
                // Only tun devices are supported on Android.
                key == "dev" -> {
                    out.add("dev tun")
                    hasDev = true
                }

                key in stripDirectives -> {
                    if (key == "auth-user-pass") needsAuth = true
                    // dropped
                }

                key == "verb" -> {
                    out.add(line)
                    hasVerb = true
                }

                key == "client" || key == "tls-client" -> {
                    out.add(line)
                    hasClient = true
                }

                else -> out.add(line)
            }
        }

        if (!hasDev) out.add("dev tun")
        if (!hasClient) out.add("client")
        if (!hasVerb) out.add("verb 4")

        // Management plumbing for the TARGET_ANDROID tun handshake: openvpn
        // connects to the app's socket, starts on hold release, and asks the
        // app for credentials that are not in an auth-user-pass file.
        out.add("management $mgmtSocketPath unix")
        out.add("management-client")
        out.add("management-hold")
        out.add("management-query-passwords")

        if (authFile != null) {
            out.add("auth-user-pass ${authFile.absolutePath}")
        }

        return Prepared(config = out.joinToString("\n"), needsAuth = needsAuth)
    }
}
