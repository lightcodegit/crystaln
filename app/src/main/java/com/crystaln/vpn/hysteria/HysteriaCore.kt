package com.crystaln.vpn.hysteria

import android.content.Context
import android.util.Log
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.xray.XrayConfigBuilder
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Hysteria2 core runner: the official hysteria client binary is shipped as an
 * executable `libhysteria.so` in jniLibs (same pattern as the OpenVPN core)
 * and started as a child process in client mode with a generated YAML config.
 *
 * It opens a local SOCKS5 proxy on 127.0.0.1:[XrayConfigBuilder.HYSTERIA_SOCKS_PORT];
 * the Xray core (which owns the TUN) is configured with a SOCKS outbound
 * pointing at it, so every established Xray-family feature (DNS via tunnel,
 * ad blocking, connectivity probe) keeps working unchanged.
 */
object HysteriaCore {

    private const val TAG = "HysteriaCore"

    @Volatile
    private var process: Process? = null

    @Volatile
    private var config: File? = null

    fun isRunning(): Boolean = process?.isAlive == true

    /**
     * Starts the hysteria client for [profile]. Blocks until the local SOCKS
     * port accepts connections (or throws). Safe to call on a worker thread.
     */
    fun start(context: Context, profile: Profile) {
        stop()
        val bin = File(context.applicationInfo.nativeLibraryDir, "libhysteria.so")
        if (!bin.exists()) {
            throw IllegalStateException("libhysteria.so is missing from the build")
        }
        val yaml = buildConfig(profile)
        val cfgFile = File(context.filesDir, "hysteria-config.yaml")
        cfgFile.writeText(yaml)
        config = cfgFile

        val pb = ProcessBuilder(
            listOf(bin.absolutePath, "client", "-c", cfgFile.absolutePath, "--log-level", "warn"),
        )
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p
        // Pump stdout/stderr to logcat so a broken config is diagnosable.
        Thread {
            p.inputStream.bufferedReader().forEachLine { line -> Log.i(TAG, line) }
        }.apply { isDaemon = true }.start()

        // Wait for the local SOCKS listener (usually up in well under a second;
        // the process dies immediately on config errors).
        repeat(40) {
            if (!p.isAlive) throw IllegalStateException("hysteria core exited — see logcat")
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
        throw IllegalStateException("hysteria core did not open its local SOCKS port")
    }

    fun stop() {
        process?.let { p ->
            try {
                p.destroy()
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: Throwable) {
            }
        }
        process = null
        config?.let { c -> if (c.name == "hysteria-config.yaml") c.delete() }
        config = null
    }

    /** Client-mode config in hysteria's own YAML schema. */
    internal fun buildConfig(profile: Profile): String {
        val sb = StringBuilder()
        sb.append("server: ${profile.address.trim()}:${profile.port}\n")
        profile.fields["password"]?.takeIf { it.isNotEmpty() }?.let {
            sb.append("auth: ${yaml(it)}\n")
        }
        // TLS section: SNI override and/or insecure skip-verify.
        val sni = profile.fields["sni"]?.takeIf { it.isNotEmpty() }
        val insecure = profile.fields["allowInsecure"] == "true"
        if (sni != null || insecure) {
            sb.append("tls:\n")
            sni?.let { sb.append("  sni: ${yaml(it)}\n") }
            if (insecure) sb.append("  insecure: true\n")
        }
        // Salamander obfuscation.
        profile.fields["obfs"]?.takeIf { it.equals("salamander", ignoreCase = true) }?.let {
            sb.append("obfs:\n  type: salamander\n  salamander:\n")
            profile.fields["obfsPassword"]?.let { p ->
                sb.append("    password: ${yaml(p)}\n")
            }
        }
        // Optional bandwidth hints (mbps). Omitted → BBR without rate limits.
        val up = profile.fields["upMbps"]?.toIntOrNull()?.takeIf { it > 0 }
        val down = profile.fields["downMbps"]?.toIntOrNull()?.takeIf { it > 0 }
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
