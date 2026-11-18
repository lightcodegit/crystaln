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
            com.crystaln.vpn.desktop.net.SecureFiles.writeText(tmp, o.toString(2))
            if (!tmp.renameTo(f)) {
                com.crystaln.vpn.desktop.net.SecureFiles.writeText(f, o.toString(2))
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

    /**
     * Kill-switch: when the tunnel drops unexpectedly, block all non-loopback
     * traffic until the user disconnects/reconnects (or the app restarts).
     * Implemented with platform firewall rules applied by the privileged
     * helper; never enabled for the plain local-proxy mode.
     */
    fun killSwitch(dataDir: File): Boolean = load(dataDir).optBoolean("killSwitch", false)

    fun setKillSwitch(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("killSwitch", v) }

    /** Default tunnel mode for Xray-family profiles. */
    enum class TunnelMode { TUN, PROXY }

    fun tunnelMode(dataDir: File): TunnelMode =
        if (load(dataDir).optString("tunnelMode") == "PROXY") TunnelMode.PROXY else TunnelMode.TUN

    fun setTunnelMode(dataDir: File, m: TunnelMode) =
        mutate(dataDir) { it.put("tunnelMode", m.name) }

    // ── appearance (parity with the Android app) ─────────────────────────

    fun themeMode(dataDir: File): ThemeMode = when (load(dataDir).optString("themeMode")) {
        "LIGHT" -> ThemeMode.LIGHT
        "DARK" -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    fun setThemeMode(dataDir: File, m: ThemeMode) = mutate(dataDir) { it.put("themeMode", m.name) }

    fun paletteId(dataDir: File): String = load(dataDir).optString("palette").ifEmpty { "indigo" }

    fun setPaletteId(dataDir: File, id: String) = mutate(dataDir) { it.put("palette", id) }

    /** Before connecting, ping all servers and pick the fastest alive one. */
    /** First-run wizard completed (or explicitly skipped). */
    fun onboarded(dataDir: File): Boolean = load(dataDir).optBoolean("onboarded", false)

    fun setOnboarded(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("onboarded", v) }

    fun autoFastest(dataDir: File): Boolean = load(dataDir).optBoolean("autoFastest", false)

    fun setAutoFastest(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("autoFastest", v) }

    /**
     * Автозагрузка при входе в систему (Windows: HKCU Run, Linux: XDG
     * autostart). Путь к бинарнику машино-специфичен — в backup не экспортируется.
     */
    fun startWithSystem(dataDir: File): Boolean = load(dataDir).optBoolean("startWithSystem", false)

    fun setStartWithSystem(dataDir: File, v: Boolean) =
        mutate(dataDir) { it.put("startWithSystem", v) }

    /** Everything restorable from a crystaln/2 backup (plus desktop-only extras). */
    fun exportJson(dataDir: File): JSONObject = JSONObject()
        .put("themeMode", themeMode(dataDir).name)
        .put("palette", paletteId(dataDir))
        .put("autoConnect", autoConnect(dataDir))
        .put("autoFastest", autoFastest(dataDir))
        .put("dnsViaTunnel", dnsViaTunnel(dataDir))
        .put("dnsServer", dnsServer(dataDir))
        .put("blockAds", blockAds(dataDir))
        .put("killSwitch", killSwitch(dataDir))
        .put("socksPort", socksPort(dataDir))
        .put("httpPort", httpPort(dataDir))
        .put("tunnelMode", tunnelMode(dataDir).name)

    fun importJson(dataDir: File, o: JSONObject) {
        mutate(dataDir) { store ->
            when (o.optString("themeMode")) {
                "LIGHT" -> store.put("themeMode", "LIGHT")
                "DARK" -> store.put("themeMode", "DARK")
                "SYSTEM" -> store.put("themeMode", "SYSTEM")
            }
            o.optString("palette").takeIf { it.isNotEmpty() }?.let { store.put("palette", it) }
            store.put("autoConnect", o.optBoolean("autoConnect", false))
            store.put("autoFastest", o.optBoolean("autoFastest", false))
            store.put("dnsViaTunnel", o.optBoolean("dnsViaTunnel", false))
            o.optString("dnsServer").takeIf { it.isNotEmpty() }?.let { store.put("dnsServer", it) }
            store.put("blockAds", o.optBoolean("blockAds", false))
            store.put("killSwitch", o.optBoolean("killSwitch", false))
            if (o.has("socksPort")) store.put("socksPort", o.optInt("socksPort", 10808).coerceIn(1024, 65535))
            if (o.has("httpPort")) store.put("httpPort", o.optInt("httpPort", 10809).coerceIn(1024, 65535))
        }
    }
}
