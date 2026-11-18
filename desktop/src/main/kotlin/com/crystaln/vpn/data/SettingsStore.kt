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

    fun anonymizeNames(dataDir: File): Boolean = load(dataDir).optBoolean("anonymizeNames", false)

    fun setAnonymizeNames(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("anonymizeNames", v) }

    /** URL probed through the tunnel to confirm the connection is live. */
    fun checkUrl(dataDir: File): String =
        load(dataDir).optString("checkUrl", "https://www.gstatic.com/generate_204")

    fun setCheckUrl(dataDir: File, v: String) {
        val u = v.trim()
        if (u.startsWith("http://", true) || u.startsWith("https://", true)) {
            mutate(dataDir) { it.put("checkUrl", u) }
        }
    }

    /** Seconds allowed for the connection test before declaring failure. */
    fun checkTimeoutSec(dataDir: File): Int = load(dataDir).optInt("checkTimeoutSec", 15).coerceIn(3, 60)

    fun setCheckTimeoutSec(dataDir: File, v: Int) =
        mutate(dataDir) { it.put("checkTimeoutSec", v.coerceIn(3, 60)) }

    /** Reconnect automatically after an unexpected tunnel drop. */
    fun autoReconnect(dataDir: File): Boolean = load(dataDir).optBoolean("autoReconnect", false)

    fun setAutoReconnect(dataDir: File, v: Boolean) = mutate(dataDir) { it.put("autoReconnect", v) }

    /** Profile used as fallback after auto-reconnect attempts are exhausted. */
    fun fallbackProfileId(dataDir: File): String? = load(dataDir).optString("fallbackProfileId").ifEmpty { null }

    fun setFallbackProfileId(dataDir: File, id: String?) =
        mutate(dataDir) {
            if (id == null) it.remove("fallbackProfileId") else it.put("fallbackProfileId", id)
        }

    /** Monthly traffic limit in MB; 0 = unlimited. */
    fun trafficLimitMb(dataDir: File): Int = load(dataDir).optInt("trafficLimitMb", 0).coerceAtLeast(0)

    fun setTrafficLimitMb(dataDir: File, v: Int) =
        mutate(dataDir) { it.put("trafficLimitMb", v.coerceAtLeast(0)) }

    /** Split tunneling mode for Xray-family profiles (routing rules). */
    enum class SplitTunnelMode { OFF, EXCLUDE, ONLY }

    fun splitTunnelMode(dataDir: File): SplitTunnelMode =
        when (load(dataDir).optString("splitTunnelMode")) {
            "EXCLUDE" -> SplitTunnelMode.EXCLUDE
            "ONLY" -> SplitTunnelMode.ONLY
            else -> SplitTunnelMode.OFF
        }

    fun setSplitTunnelMode(dataDir: File, m: SplitTunnelMode) =
        mutate(dataDir) { it.put("splitTunnelMode", m.name) }

    /** Domains / IPs / CIDRs the split-tunnel rules apply to. */
    fun splitTunnelRules(dataDir: File): List<String> {
        val a = load(dataDir).optJSONArray("splitTunnelRules") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            a.optString(i).trim().takeIf { it.isNotEmpty() }
        }
    }

    fun setSplitTunnelRules(dataDir: File, rules: List<String>) =
        mutate(dataDir) {
            it.put("splitTunnelRules", org.json.JSONArray(rules.filter { r -> r.isNotBlank() }))
        }

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
        .put("anonymizeNames", anonymizeNames(dataDir))
        .put("checkUrl", checkUrl(dataDir))
        .put("checkTimeoutSec", checkTimeoutSec(dataDir))
        .put("autoReconnect", autoReconnect(dataDir))
        .put("fallbackProfileId", fallbackProfileId(dataDir) ?: "")
        .put("trafficLimitMb", trafficLimitMb(dataDir))
        .put("splitTunnelMode", splitTunnelMode(dataDir).name)
        .put("splitTunnelRules", org.json.JSONArray(splitTunnelRules(dataDir)))
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
            store.put("anonymizeNames", o.optBoolean("anonymizeNames", false))
            o.optString("checkUrl").takeIf { it.startsWith("http") }?.let { store.put("checkUrl", it) }
            if (o.has("checkTimeoutSec")) store.put("checkTimeoutSec", o.optInt("checkTimeoutSec", 15).coerceIn(3, 60))
            store.put("autoReconnect", o.optBoolean("autoReconnect", false))
            o.optString("fallbackProfileId").takeIf { it.isNotEmpty() }?.let { store.put("fallbackProfileId", it) }
            if (o.has("trafficLimitMb")) store.put("trafficLimitMb", o.optInt("trafficLimitMb", 0).coerceAtLeast(0))
            when (o.optString("splitTunnelMode")) {
                "EXCLUDE" -> store.put("splitTunnelMode", "EXCLUDE")
                "ONLY" -> store.put("splitTunnelMode", "ONLY")
                "OFF" -> store.put("splitTunnelMode", "OFF")
            }
            o.optJSONArray("splitTunnelRules")?.let { store.put("splitTunnelRules", it) }
            store.put("killSwitch", o.optBoolean("killSwitch", false))
            if (o.has("socksPort")) store.put("socksPort", o.optInt("socksPort", 10808).coerceIn(1024, 65535))
            if (o.has("httpPort")) store.put("httpPort", o.optInt("httpPort", 10809).coerceIn(1024, 65535))
        }
    }
}

