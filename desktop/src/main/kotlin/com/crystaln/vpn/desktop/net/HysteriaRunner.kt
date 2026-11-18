package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.xray.XrayConfigBuilder
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Hysteria2 core runner for the desktop app: the official hysteria client
 * binary (downloaded by [Binaries]) runs as a child process in client mode
 * with a generated YAML config. It opens a local SOCKS5 proxy on
 * 127.0.0.1:[XrayConfigBuilder.HYSTERIA_SOCKS_PORT]; the xray outbound then
 * simply points there (works for both proxy and TUN modes).
 */
internal object HysteriaRunner {

    var process: ChildProcess? = null

    /** The config file currently backing [process] (removed on stop). */
    private var configFile: File? = null

    fun isRunning(): Boolean = process?.alive == true

    /**
     * Starts the hysteria client for [profile] and waits for the local SOCKS
     * port. Throws with a human-readable message on failure.
     */
    fun start(p: Profile) {
        stop()
        val bin = AppDirs.binDir()
        val exe = File(bin, com.crystaln.vpn.desktop.Platform.binName(Binaries.HYSTERIA))
        if (!exe.exists()) {
            throw IllegalStateException("Бинарник hysteria не найден — нажмите «Обновить бинарники»")
        }
        val cfgFile = File(AppDirs.dataDir(), "hysteria-config.yaml")
        SecureFiles.writeText(cfgFile, buildConfig(p))
        configFile = cfgFile

        val proc = ChildProcess(
            "hysteria",
            listOf(
                ChildProcess.bin(bin, Binaries.HYSTERIA),
                "client", "-c", cfgFile.absolutePath, "--log-level", "warn",
            ),
        ) { TunnelManager.logLine("[hysteria] $it") }
        process = proc

        // Wait for the local SOCKS listener; hysteria dies immediately on
        // bad configs, so also watch for early exit.
        repeat(40) {
            if (proc.alive != true) {
                stop()
                throw IllegalStateException("hysteria завершился сразу — смотрите журнал")
            }
            val up = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", XrayConfigBuilder.HYSTERIA_SOCKS_PORT), 250)
                    true
                }
            } catch (_: Throwable) {
                false
            }
            if (up) return
            Thread.sleep(250)
        }
        stop()
        throw IllegalStateException("hysteria не открыл локальный SOCKS-порт")
    }

    fun stop() {
        process?.stop()
        process = null
        configFile?.takeIf { it.name == "hysteria-config.yaml" }?.delete()
        configFile = null
    }

    /** Client-mode config in hysteria's own YAML schema. */
    internal fun buildConfig(p: Profile): String {
        val sb = StringBuilder()
        sb.append("server: ${p.address.trim()}:${p.port}\n")
        p.fields["password"]?.takeIf { it.isNotEmpty() }?.let {
            sb.append("auth: ${yaml(it)}\n")
        }
        val sni = p.fields["sni"]?.takeIf { it.isNotEmpty() }
        val insecure = p.fields["allowInsecure"] == "true"
        if (sni != null || insecure) {
            sb.append("tls:\n")
            sni?.let { sb.append("  sni: ${yaml(it)}\n") }
            if (insecure) sb.append("  insecure: true\n")
        }
        p.fields["obfs"]?.takeIf { it.equals("salamander", ignoreCase = true) }?.let {
            sb.append("obfs:\n  type: salamander\n  salamander:\n")
            p.fields["obfsPassword"]?.let { pw -> sb.append("    password: ${yaml(pw)}\n") }
        }
        val up = p.fields["upMbps"]?.toIntOrNull()?.takeIf { it > 0 }
        val down = p.fields["downMbps"]?.toIntOrNull()?.takeIf { it > 0 }
        if (up != null || down != null) {
            sb.append("bandwidth:\n")
            up?.let { sb.append("  up: $it mbps\n") }
            down?.let { sb.append("  down: $it mbps\n") }
        }
        sb.append("socks5:\n")
        sb.append("  listen: 127.0.0.1:${XrayConfigBuilder.HYSTERIA_SOCKS_PORT}\n")
        sb.append("  disableUDP: false\n")
        return sb.toString()
    }

    /** Minimal YAML scalar quoting for values that may contain special chars. */
    private fun yaml(v: String): String {
        val needsQuote = v.isEmpty() || v.any { c ->
            c in ":#{}[]&*!|>'\"%@`,\n" || c == ' ' || c.code < 32
        }
        return if (needsQuote) "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else v
    }
}
