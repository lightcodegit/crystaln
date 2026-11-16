package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONObject
/** App-level settings persisted in shared prefs. */
object SettingsStore {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "crystaln"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_PALETTE = "palette"
    private const val KEY_AUTOCONNECT = "auto_connect"
    private const val KEY_AUTO_FASTEST = "auto_fastest"
    private const val KEY_DNS_TUNNEL = "dns_via_tunnel"
    private const val KEY_DNS_SERVER = "dns_server"
    private const val KEY_BLOCK_ADS = "block_ads"
    private const val KEY_WALLPAPER = "wallpaper"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_GYRO = "gyro_background"
    private const val KEY_AMOLED = "amoled_mode"
    private const val KEY_APP_LOCK = "app_lock"

    /** First-run wizard completed (or explicitly skipped). */
    fun onboarded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    /** Named accent palette id (see ui.theme.CrystalPalettes). */
    fun paletteId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PALETTE, null) ?: "indigo"

    fun setPaletteId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PALETTE, id).apply()
    }

    fun themeMode(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
    }

    /** Material You dynamic color palette (Android 12+). */
    fun dynamicColor(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC, false)

    fun setDynamicColor(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC, value).apply()
    }

    /** Connect the last selected profile automatically on app launch. */
    fun autoConnect(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOCONNECT, false)

    fun setAutoConnect(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTOCONNECT, value).apply()
    }

    /**
     * Before connecting (app launch / auto-connect), ping every profile and
     * select the reachable one with the lowest latency instead of the last
     * manually selected profile.
     */
    fun autoFastest(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_FASTEST, false)

    fun setAutoFastest(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_FASTEST, value).apply()
    }

    /**
     * Intercept DNS (port 53) in the TUN and forward it through the tunnel
     * instead of letting the system resolver talk to the ISP. Xray profiles.
     */
    fun dnsViaTunnel(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DNS_TUNNEL, false)

    fun setDnsViaTunnel(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DNS_TUNNEL, value).apply()
    }

    /** Upstream DNS used when [dnsViaTunnel] is on. */
    fun dnsServer(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DNS_SERVER, "1.1.1.1") ?: "1.1.1.1"

    fun setDnsServer(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DNS_SERVER, value.trim()).apply()
    }

    /** Drop connections to ad/tracker domains (requires geosite.dat in filesDir). */
    fun blockAds(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_ADS, false)

    fun setBlockAds(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLOCK_ADS, value).apply()
    }

    /**
     * Custom background image, stored as a file name inside filesDir
     * (never a raw path, so a compromised prefs file can't point elsewhere).
     */
    fun wallpaper(context: Context): String? {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER, null) ?: return null
        // Only accept a bare file name to rule out path traversal.
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        return name
    }

    fun setWallpaper(context: Context, fileName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_WALLPAPER, fileName).apply()
    }

    /**
     * Parallax background: the color blobs shift with the phone's movement
     * (gyroscope). Off by default — it keeps a sensor active while the app
     * is open.
     */
    fun gyroBackground(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GYRO, false)

    fun setGyroBackground(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_GYRO, value).apply()
    }

    /**
     * AMOLED mode: pure-black background and surfaces in the dark theme
     * (pixels are fully off — battery savings on OLED panels). No effect
     * in the light theme.
     */
    fun amoledMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AMOLED, false)

    fun setAmoledMode(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AMOLED, value).apply()
    }

    /**
     * App lock: the profile list is sensitive, so ask for a fingerprint (or
     * device credential) before showing anything.
     */
    fun appLock(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK, false)

    fun setAppLock(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_APP_LOCK, value).apply()
    }

    /** Everything restorable from a backup. The wallpaper image is a binary
     * asset in filesDir, not prefs — it is not carried in backups. */
    fun exportJson(context: Context): JSONObject = JSONObject()
        .put("themeMode", themeMode(context).name)
        .put("dynamicColor", dynamicColor(context))
        .put("palette", paletteId(context))
        .put("autoConnect", autoConnect(context))
        .put("autoFastest", autoFastest(context))
        .put("dnsViaTunnel", dnsViaTunnel(context))
        .put("dnsServer", dnsServer(context))
        .put("blockAds", blockAds(context))

    fun importJson(context: Context, o: JSONObject) {
        setThemeMode(
            context,
            runCatching { ThemeMode.valueOf(o.optString("themeMode")) }.getOrDefault(ThemeMode.SYSTEM),
        )
        setDynamicColor(context, o.optBoolean("dynamicColor", false))
        o.optString("palette").takeIf { it.isNotEmpty() }?.let { setPaletteId(context, it) }
        setAutoConnect(context, o.optBoolean("autoConnect", false))
        setAutoFastest(context, o.optBoolean("autoFastest", false))
        setDnsViaTunnel(context, o.optBoolean("dnsViaTunnel", false))
        o.optString("dnsServer").takeIf { it.isNotEmpty() }?.let { setDnsServer(context, it) }
        setBlockAds(context, o.optBoolean("blockAds", false))
    }
}
