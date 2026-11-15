package com.crystaln.vpn.data

import org.json.JSONObject
import java.io.File

/**
 * Desktop settings (JSON file in the config dir). A subset of the Android
 * app's settings that makes sense off-phone; theme is handled by the OS and
 * the Compose theme.
 */
object SettingsStore {

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private const val FILE = "settings.json"

    private val lock = Any()

    @Volatile
    private var cache: JSONObject? = null

    @Volatile
    private var loadedDir: File? = null

    internal fun resetForTest() {
        synchronized(lock) {
            cache = null
            loadedDir = null
        }
    }

    private fun file(dataDir: File) = File(dataDir, FILE)

    private fun load(dataDir: File): JSONObject = synchronized(lock) {
        if (cache != null && loadedDir == dataDir) return cache as JSONObject
        val o = try {
            val f = file(dataDir)
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        cache = o
        loadedDir = dataDir
        o
    }

    private fun save(dataDir: File, o: JSONObject) {
        dataDir.mkdirs()
        val f = file(dataDir)
        val tmp = File(dataDir, "$FILE.tmp")
        try {
            tmp.writeText(o.toString(2))
            if (!tmp.renameTo(f)) {
                f.writeText(o.toString(2))
                tmp.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    private fun mutate(dataDir: File, block: (JSONObject) -> Unit) = synchronized(lock) {
        val o = load(dataDir)
        block(o)
        save(dataDir, o)
    }

    // ── connection behavior ──────────────────────────────────────────────

    /** Connect the selected profile automatically on app launch. */
    fun autoConnect(dataDir: File): Boolean = load(dataDir).optBoolean("autoConnect", false)

    fun setAutoConnect(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("autoConnect", v) }

    /** Local proxy ports for the no-root mode. */
    fun socksPort(dataDir: File): Int = load(dataDir).optInt("socksPort", 10808)

    fun setSocksPort(dataDir: File, v: Int) = mutate(dataDir) { it.put("socksPort", v.coerceIn(1024, 65535)) }

    fun httpPort(dataDir: File): Int = load(dataDir).optInt("httpPort", 10809)

    fun setHttpPort(dataDir: File, v: Int) = mutate(dataDir) { it.put("httpPort", v.coerceIn(1024, 65535)) }

    /** Anti-leak: intercept DNS inside the TUN and resolve via the tunnel. */
    fun dnsViaTunnel(dataDir: File): Boolean = load(dataDir).optBoolean("dnsViaTunnel", false)

    fun setDnsViaTunnel(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("dnsViaTunnel", v) }

    fun dnsServer(dataDir: File): String = load(dataDir).optString("dnsServer", "1.1.1.1")

    fun setDnsServer(dataDir: File, v: String) = mutate(dataDir) { it.put("dnsServer", v.trim()) }

    /** Drop ad/tracker domains (needs geosite.dat next to the xray binary). */
    fun blockAds(dataDir: File): Boolean = load(dataDir).optBoolean("blockAds", false)

    fun setBlockAds(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("blockAds", v) }

    /** Default tunnel mode for Xray-family profiles. */
    enum class TunnelMode { TUN, PROXY }

    fun tunnelMode(dataDir: File): TunnelMode =
        if (load(dataDir).optString("tunnelMode") == "PROXY") TunnelMode.PROXY else TunnelMode.TUN

    fun setTunnelMode(dataDir: File, m: TunnelMode) =
        mutate(dataDir) { it.put("tunnelMode", m.name) }
}
