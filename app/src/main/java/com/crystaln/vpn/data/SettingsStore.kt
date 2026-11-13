package com.crystaln.vpn.data

import android.content.Context

/** App-level settings persisted in shared prefs. */
object SettingsStore {
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "crystaln"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_AUTOCONNECT = "auto_connect"
    private const val KEY_WALLPAPER = "wallpaper"

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
}
