package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.xray.XrayConfigBuilder
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

internal object SingBoxRunner {

    var process: ChildProcess? = null
    private var configFile: File? = null

    fun isRunning(): Boolean = process?.alive == true

    fun start(p: Profile) {
        stop()
        val bin = AppDirs.binDir()
        val exe = File(bin, com.crystaln.vpn.desktop.Platform.binName(Binaries.SINGBOX))
        if (!exe.exists()) {
            throw IllegalStateException("Бинарник sing-box не найден — нажмите «Обновить бинарники»")
        }
        val cfgFile = File(AppDirs.dataDir(), "singbox-config.json")
        cfgFile.writeText(SingBoxCoreConfig.build(p))
        configFile = cfgFile

        process = ChildProcess(
            "singbox",
            listOf(ChildProcess.bin(bin, Binaries.SINGBOX), "run", "-c", cfgFile.absolutePath),
        ) { TunnelManager.logLine("[singbox] $it") }

        repeat(40) {
            if (process?.alive != true) {
                stop()
                throw IllegalStateException("sing-box завершился сразу — смотрите журнал")
            }
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
        throw IllegalStateException("sing-box не открыл локальный mixed-порт")
    }

    fun stop() {
        process?.stop()
        process = null
        configFile?.takeIf { it.name == "singbox-config.json" }?.delete()
        configFile = null
    }
}

/** Shared config builder (mirrors Android SingBoxCore.buildConfig). */
internal object SingBoxCoreConfig {
    fun build(profile: Profile): String {
        val outbound = when (profile.protocol) {
            com.crystaln.vpn.data.Protocol.TUIC -> JSONObject().apply {
                put("type", "tuic"); put("server", profile.address.trim()); put("server_port", profile.port)
                put("uuid", profile.fields["uuid"] ?: ""); put("password", profile.fields["password"] ?: "")
                val tls = JSONObject().put("enabled", true)
                profile.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { tls.put("server_name", it) }
                if (profile.fields["allowInsecure"] == "true") tls.put("insecure", true)
                put("tls", tls)
                profile.fields["congestionControl"]?.takeIf { it.isNotEmpty() }?.let { put("congestion_control", it) }
                profile.fields["udpRelayMode"]?.takeIf { it.isNotEmpty() }?.let { put("udp_relay_mode", it) }
            }
            com.crystaln.vpn.data.Protocol.ANYTLS -> JSONObject().apply {
                put("type", "anytls"); put("server", profile.address.trim()); put("server_port", profile.port)
                put("password", profile.fields["password"] ?: "")
                val tls = JSONObject().put("enabled", true)
                profile.fields["sni"]?.takeIf { it.isNotEmpty() }?.let { tls.put("server_name", it) }
                if (profile.fields["allowInsecure"] == "true") tls.put("insecure", true)
                put("tls", tls)
            }
            com.crystaln.vpn.data.Protocol.HYSTERIA1 -> JSONObject().apply {
                put("type", "hysteria"); put("server", profile.address.trim()); put("server_port", profile.port)
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
        root.put("inbounds", org.json.JSONArray().put(JSONObject().apply {
            put("type", "mixed"); put("listen", "127.0.0.1"); put("listen_port", XrayConfigBuilder.SINGBOX_MIXED_PORT)
        }))
        root.put("outbounds", org.json.JSONArray().put(outbound))
        return root.toString(2)
    }
}
