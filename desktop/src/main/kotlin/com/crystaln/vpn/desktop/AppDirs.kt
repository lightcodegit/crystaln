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
        if (Platform.isWindows) {
            val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/AppData/Roaming")
            return File(base, "CrystalN")
        }
        if (Platform.isMac) {
            return File(System.getProperty("user.home"), "Library/Application Support/CrystalN")
        }
        val base = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.config")
        return File(base, "crystaln")
    }

    fun dataDir(): File {
        if (Platform.isWindows) {
            val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + "/AppData/Local")
            return File(base, "CrystalN")
        }
        if (Platform.isMac) {
            return File(System.getProperty("user.home"), "Library/Application Support/CrystalN")
        }
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.local/share")
        return File(base, "crystaln")
    }

    fun binDir(): File = File(dataDir(), "bin")

    fun logDir(): File = File(dataDir(), "logs")
}
