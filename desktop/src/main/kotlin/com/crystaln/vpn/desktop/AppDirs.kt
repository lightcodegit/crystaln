package com.crystaln.vpn.desktop

import java.io.File

/**
 * App directories, XDG-style:
 * - config (profiles, subscriptions, settings): $XDG_CONFIG_HOME/crystaln,
 *   default ~/.config/crystaln
 * - data (runtime state, logs): $XDG_DATA_HOME/crystaln, default
 *   ~/.local/share/crystaln
 * - external binaries (xray, tun2socks, openvpn) live in the data dir's
 *   `bin/` and are auto-downloaded on first use.
 */
object AppDirs {

    fun configDir(): File {
        val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.config")
        return File(base, "crystaln")
    }

    fun dataDir(): File {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.local/share")
        return File(base, "crystaln")
    }

    fun binDir(): File = File(dataDir(), "bin")

    fun logDir(): File = File(dataDir(), "logs")
}
