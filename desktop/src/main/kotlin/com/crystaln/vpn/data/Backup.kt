package com.crystaln.vpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Full backup ("settings + subscriptions + profiles"), format crystaln/2.
 * Desktop port of the Android file: [File] data dir instead of a Context,
 * same JSON formats — Android and desktop backups are interchangeable.
 *
 * The old crystaln/1 export kept only profiles; the same vault encryption
 * (see [ProfileVault]) wraps this payload, so a password-protected backup
 * now restores the whole app configuration, not just servers.
 *
 * crystaln/3 is a settings-only export (no profiles, no subscriptions) for
 * moving preferences between machines without touching the server list.
 */
object Backup {

    /** Profile-only exports stay crystaln/1; this is the superset. */
    fun exportFull(dataDir: File): String = JSONObject()
        .put("format", "crystaln/2")
        .put("selected", ProfileStore.selectedId ?: "")
        .put(
            "profiles",
            JSONArray().apply { ProfileStore.load(dataDir).forEach { put(it.toJson()) } },
        )
        .put("subscriptions", SubscriptionsStore.exportJson(dataDir))
        .put("settings", SettingsStore.exportJson(dataDir))
        .toString(2)

    /** Settings only — no profiles, no subscriptions. */
    fun exportSettingsOnly(dataDir: File): String = JSONObject()
        .put("format", "crystaln/3")
        .put("settings", SettingsStore.exportJson(dataDir))
        .toString(2)

    /**
     * Imports a crystaln/2 backup: settings first, then subscriptions, then
     * profiles (via the normal merge — re-imports update in place). Returns
     * a user-facing summary, or null when the text is not a crystaln/2 backup
     * (caller falls back to the profile-only import path).
     */
    fun importFull(dataDir: File, text: String): String? {
        val root = try {
            JSONObject(text.trim())
        } catch (_: Exception) {
            return null
        }
        if (root.optString("format") != "crystaln/2") return null

        root.optJSONObject("settings")?.let { SettingsStore.importJson(dataDir, it) }
        val subs = root.optJSONArray("subscriptions")
            ?.let { SubscriptionsStore.importJson(dataDir, it) } ?: 0

        // Profiles ride the existing crystaln/1 merge path (dedupe + selection).
        val parsed = ProfileImporter.parseText(text)
        val merged = if (parsed.isNotEmpty()) {
            ProfileStore.mergeProfiles(dataDir, parsed, subscriptionId = null, selectFirst = true)
        } else null

        return buildString {
            if (merged != null) {
                append("Профили: ${merged.added + merged.updated}")
            }
            if (subs > 0) {
                if (isNotEmpty()) append(" · ")
                append("Подписки: $subs")
            }
            if (isEmpty()) append("Настройки восстановлены")
        }
    }

    /**
     * Imports a crystaln/3 settings-only export. Returns a user-facing
     * summary, or null when the text is not a crystaln/3 backup.
     */
    fun importSettingsOnly(dataDir: File, text: String): String? {
        val root = try {
            JSONObject(text.trim())
        } catch (_: Exception) {
            return null
        }
        if (root.optString("format") != "crystaln/3") return null
        root.optJSONObject("settings")?.let { SettingsStore.importJson(dataDir, it) }
            ?: return null
        return "Настройки восстановлены"
    }
}
