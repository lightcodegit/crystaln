package com.crystaln.vpn.singbox

import android.content.Context
import android.util.Log
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.xray.XrayConfigBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * sing-box core runner: the official sing-box binary is shipped as an
 * executable `libsingbox.so` in jniLibs (same pattern as the OpenVPN core)
 * and started as a child process with a generated JSON config.
 *
 * It opens a local mixed (SOCKS5+HTTP) inbound on
 * 127.0.0.1:[XrayConfigBuilder.SINGBOX_MIXED_PORT]; the Xray core (which owns
 * the TUN) is configured with a SOCKS outbound pointing at it.
 */
object SingBoxCore {

    private const val TAG = "SingBoxCore"

    @Volatile
    private var process: Process? = null

    fun isRunning(): Boolean = process?.isAlive == true

    fun start(context: Context, profile: Profile) {
        stop()
        val bin = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        if (!bin.exists()) throw IllegalStateException("libsingbox.so is missing from the build")
        val cfgFile = File(context.filesDir, "singbox-config.json")
        cfgFile.writeText(buildConfig(profile))
        val pb = ProcessBuilder(listOf(bin.absolutePath, "run", "-c", cfgFile.absolutePath))
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p
        Thread {
            p.inputStream.bufferedReader().forEachLine { line ->
                // Engine logs can echo auth/uuid/password from failed configs.
                // Only forward lines that don't look credential-bearing.
                val lower = line.lowercase()
                if (listOf("password", "passwd", "auth:", "uuid:", "private", "token", "secret", "key:")
                        .any { lower.contains(it) }
                ) {
                    Log.i(TAG, "[redacted]")
                } else {
                    Log.i(TAG, line)
                }
            }
        }.apply { isDaemon = true }.start()

        repeat(40) {
            if (!p.isAlive) throw IllegalStateException("sing-box core exited — see logcat")
            val up = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", XrayConfigBuilder.SINGBOX_MIXED_PORT), 250)
                    true
                }
            } catch (_: Throwable) { false }
            if (up) return
            Thread.sleep(250)
        }
        stop()
        throw IllegalStateException("sing-box core did not open its mixed port")
    }

    fun stop() {
        process?.let { p ->
            try {
                p.destroy()
                if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            } catch (_: Throwable) { }
        }
        process = null
    }

    internal fun buildConfig(profile: Profile): String {
        val outbound = when (profile.protocol) {
            com.crystaln.vpn.data.Protocol.TUIC -> JSONObject().apply {
                put("type", "tuic")
                put("server", profile.address.trim())
                put("server_port", profile.port)
                put("uuid", profile.fields["uuid"] ?: "")
                put("password", profile.fields["password"] ?: "")
                val tls = JSONObject().put("enabled", true)
                profile.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { tls.put("server_name", it) }
                if (profile.fields["allowInsecure"] == "true") tls.put("insecure", true)
                put("tls", tls)
                profile.fields["congestionControl"]?.takeIf { it.isNotEmpty() }?.let { put("congestion_control", it) }
                profile.fields["udpRelayMode"]?.takeIf { it.isNotEmpty() }?.let { put("udp_relay_mode", it) }
            }
            com.crystaln.vpn.data.Protocol.ANYTLS -> JSONObject().apply {
                put("type", "anytls")
                put("server", profile.address.trim())
                put("server_port", profile.port)
                put("password", profile.fields["password"] ?: "")
                val tls = JSONObject().put("enabled", true)
                profile.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { tls.put("server_name", it) }
                if (profile.fields["allowInsecure"] == "true") tls.put("insecure", true)
                put("tls", tls)
            }
            com.crystaln.vpn.data.Protocol.HYSTERIA1 -> JSONObject().apply {
                put("type", "hysteria")
                put("server", profile.address.trim())
                put("server_port", profile.port)
                put("auth", profile.fields["password"] ?: "")
                val tls = JSONObject().put("enabled", true)
                profile.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { tls.put("server_name", it) }
                if (profile.fields["allowInsecure"] == "true") tls.put("insecure", true)
                put("tls", tls)
                profile.fields["upMbps"]?.toIntOrNull()?.takeIf { it > 0 }?.let { put("up_mbps", it) }
                profile.fields["downMbps"]?.toIntOrNull()?.takeIf { it > 0 }?.let { put("down_mbps", it) }
            }
            else -> JSONObject().put("type", "direct")
        }
        val root = JSONObject()
        root.put("inbounds", JSONArray().put(JSONObject().apply {
            put("type", "mixed")
            put("listen", "127.0.0.1")
            put("listen_port", XrayConfigBuilder.SINGBOX_MIXED_PORT)
        }))
        root.put("outbounds", JSONArray().put(outbound))
        return root.toString(2)
    }
}
