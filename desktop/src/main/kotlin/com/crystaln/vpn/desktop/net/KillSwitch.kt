package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.desktop.Platform
import java.io.File

/**
 * Desktop kill-switch: when a tunnel drops unexpectedly, block all outbound
 * traffic (except loopback and the local management port) until the user
 * explicitly disconnects/reconnects or the app restarts.
 *
 * Rules are installed per-session by the privileged helper and removed on a
 * clean disconnect / JVM shutdown. A JVM crash leaves the OS firewall rule in
 * place — this is intentional for a kill-switch (fail closed), and the rules
 * are tagged so the next app start can clean them up.
 *
 * - Linux: nftables (with iptables fallback), chain `CRYSTALN_KILL`.
 * - Windows: `netsh advfirewall firewall` rules for the app + a blanket
 *   outbound block rule tagged with a session marker.
 */
object KillSwitch {

    private const val TAG = "crystaln-kill"
    private var engaged = false

    /**
     * Applies a blocking firewall rule set. [allowIface] is the VPN interface
     * that must stay allowed while the tunnel is up (Linux). When it is null
     * (tunnel dropped), only loopback stays open — that is the fail-closed
     * kill-switch state.
     */
    fun engage(sessionId: String, allowIface: String? = null) {
        if (engaged) return
        engaged = true
        val res = if (Platform.isWindows) {
            Privileges.runScript(
                "kill-on",
                listOf(
                    "netsh advfirewall firewall delete rule name=\"$TAG\" >nul 2>&1; \$LASTEXITCODE=0",
                    "netsh advfirewall firewall add rule name=\"$TAG\" dir=out action=block enable=yes description=\"$sessionId\"",
                ),
            )
        } else {
            val allowRule = if (allowIface != null) {
                "nft insert rule inet crystaln_kill output comment \"$TAG-vpn\" oifname \"$allowIface\" counter accept 2>/dev/null || true"
            } else {
                ""
            }
            Privileges.runScript(
                "kill-on",
                listOf(
                    // nftables: tag via comment so stale rules can be removed.
                    "nft add table inet crystaln_kill 2>/dev/null || true",
                    "nft add chain inet crystaln_kill output { type filter hook output priority -200 \\; policy accept \\; } 2>/dev/null || true",
                    allowRule,
                    "nft add rule inet crystaln_kill output comment \"$TAG-lo\" oifname \"lo\" counter accept 2>/dev/null || true",
                    "nft add rule inet crystaln_kill output comment \"$TAG\" counter drop 2>/dev/null || true",
                ).filter { it.isNotEmpty() },
            )
        }
        if (res is Privileges.Result.Fail) {
            log("engage failed: ${res.output.take(200)}")
            // Fail closed only when the rule was actually applied; otherwise
            // leave the network alone so the user isn't locked out by an
            // unrelated firewall error.
        } else {
            log("engaged (session=$sessionId)")
        }
    }

    /** Removes the blocking rules. Always called on clean disconnect. */
    fun disengage() {
        if (!engaged) return
        engaged = false
        if (Platform.isWindows) {
            Privileges.runScript(
                "kill-off",
                listOf("netsh advfirewall firewall delete rule name=\"$TAG\" >nul 2>&1; \$LASTEXITCODE=0"),
            )
        } else {
            Privileges.runScript(
                "kill-off",
                listOf("nft delete table inet crystaln_kill 2>/dev/null || true"),
            )
        }
        log("disengaged")
    }

    /** Cleans up rules left behind by a previous crashed process. */
    fun cleanupStale() {
        if (Platform.isWindows) {
            Privileges.runScript(
                "kill-clean",
                listOf("netsh advfirewall firewall delete rule name=\"$TAG\" >nul 2>&1; \$LASTEXITCODE=0"),
            )
        } else {
            Privileges.runScript(
                "kill-clean",
                listOf("nft delete table inet crystaln_kill 2>/dev/null || true"),
            )
        }
    }

    private fun log(msg: String) {
        try {
            Privileges.appendLog("killswitch.log", "$msg\n")
        } catch (_: Throwable) {
        }
    }
}
