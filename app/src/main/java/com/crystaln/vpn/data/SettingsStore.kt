package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONObject
/** App-level settings persisted in shared prefs. */
object SettingsStore {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /** Default URL probed to confirm the tunnel actually carries traffic. */
    const val DEFAULT_CHECK_URL = "https://www.gstatic.com/generate_204"

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
    private const val KEY_LIQUID = "liquid_background"
    private const val KEY_ANON_NAMES = "anonymize_names"
    private const val KEY_CHECK_URL = "check_url"
    private const val KEY_CHECK_TIMEOUT = "check_timeout_sec"
    private const val KEY_SPLIT_MODE = "split_mode"
    private const val KEY_SPLIT_APPS = "split_apps"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_FALLBACK_PROFILE = "fallback_profile"
    private const val KEY_TRAFFIC_LIMIT_MB = "traffic_limit_mb"
    private const val KEY_TRAFFIC_LIMIT_MONTH = "traffic_limit_month"
    private const val KEY_TRAFFIC_LIMIT_USED = "traffic_limit_used"

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

    /**
     * Liquid mode: the background color blobs behave like real liquid —
     * they pool toward the tilted side, slosh from shakes and wobble.
     * Implies gyroscope reaction.
     */
    fun liquidBackground(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIQUID, false)

    fun setLiquidBackground(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LIQUID, value).apply()
    }

    /** Split-tunnel mode: 0 = off (all traffic via VPN), 1 = exclude selected
     *  apps from the VPN, 2 = only selected apps use the VPN. */
    fun splitMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SPLIT_MODE, 0)

    fun setSplitMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SPLIT_MODE, mode.coerceIn(0, 2)).apply()
    }

    /** Comma-separated package names participating in split tunneling. */
    fun splitApps(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SPLIT_APPS, "")?.split(",")?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    fun setSplitApps(context: Context, pkgs: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SPLIT_APPS, pkgs.joinToString(",")).apply()
    }

    fun autoReconnect(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECONNECT, false)

    fun setAutoReconnect(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_RECONNECT, value).apply()
    }

    fun fallbackProfileId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK_PROFILE, null)

    fun setFallbackProfileId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FALLBACK_PROFILE, id).apply()
    }

    /** Monthly traffic limit in MB (0 = off). */
    fun trafficLimitMb(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TRAFFIC_LIMIT_MB, 0)

    fun setTrafficLimitMb(context: Context, mb: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TRAFFIC_LIMIT_MB, mb.coerceAtLeast(0)).apply()
    }

    /** Month key ("YYYY-MM") the accumulated [trafficLimitUsedMb] belongs to. */
    fun trafficLimitMonth(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRAFFIC_LIMIT_MONTH, "") ?: ""

    fun trafficLimitUsedMb(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TRAFFIC_LIMIT_USED, 0L)

    /** Adds [mb] (may be fractional via KB) to this month's counter, rolling
     *  over automatically when the month changes. Returns the new total. */
    fun addTrafficLimitUsage(context: Context, bytes: Long): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
            .format(java.util.Date())
        val storedMonth = prefs.getString(KEY_TRAFFIC_LIMIT_MONTH, "") ?: ""
        val current = if (storedMonth == now) prefs.getLong(KEY_TRAFFIC_LIMIT_USED, 0L) else 0L
        val next = current + bytes / (1024 * 1024)
        prefs.edit()
            .putString(KEY_TRAFFIC_LIMIT_MONTH, now)
            .putLong(KEY_TRAFFIC_LIMIT_USED, next)
            .apply()
        return next
    }

    /**
     * Privacy mask: profile names are replaced in the UI with a country flag
     * (guessed from the server hostname) and an index — "🇩🇪 #4". Names stay
     * stored; only what's shown on screen is masked.
     */
    fun anonymizeNames(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANON_NAMES, false)

    fun setAnonymizeNames(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANON_NAMES, value).apply()
    }

    /** URL probed (through the tunnel) to confirm the connection is live. */
    fun checkUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CHECK_URL, DEFAULT_CHECK_URL) ?: DEFAULT_CHECK_URL

    fun setCheckUrl(context: Context, value: String) {
        val url = value.trim()
        // Only accept http(s) URLs — anything else can't serve as a probe.
        if (url.startsWith("http://") || url.startsWith("https://")) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_CHECK_URL, url).apply()
        }
    }

    /** Seconds allowed for the connection test before declaring failure. */
    fun checkTimeoutSec(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CHECK_TIMEOUT, 15)

    fun setCheckTimeoutSec(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_CHECK_TIMEOUT, value.coerceIn(3, 60)).apply()
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
        .put("splitMode", splitMode(context))
        .put("splitApps", splitApps(context).joinToString(","))
        .put("autoReconnect", autoReconnect(context))
        .put("fallbackProfileId", fallbackProfileId(context) ?: "")
        .put("trafficLimitMb", trafficLimitMb(context))
        .put("anonymizeNames", anonymizeNames(context))
        .put("checkUrl", checkUrl(context))
        .put("checkTimeoutSec", checkTimeoutSec(context))

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
        setSplitMode(context, o.optInt("splitMode", 0))
        setSplitApps(context, o.optString("splitApps").split(",").filter { it.isNotEmpty() }.toSet())
        setAutoReconnect(context, o.optBoolean("autoReconnect", false))
        o.optString("fallbackProfileId").takeIf { it.isNotEmpty() }?.let { setFallbackProfileId(context, it) }
        setTrafficLimitMb(context, o.optInt("trafficLimitMb", 0))
        setAnonymizeNames(context, o.optBoolean("anonymizeNames", false))
        o.optString("checkUrl").takeIf { it.isNotEmpty() }?.let { setCheckUrl(context, it) }
        o.optInt("checkTimeoutSec", 0).takeIf { it > 0 }?.let { setCheckTimeoutSec(context, it) }
    }
}
