package com.crystaln.vpn.data

import android.content.Context
import com.crystaln.vpn.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full backup ("settings + subscriptions + profiles"), format crystaln/2.
 *
 * The old crystaln/1 export kept only profiles; the same vault encryption
 * (see [ProfileVault]) wraps this payload, so a password-protected backup
 * now restores the whole app configuration, not just servers.
 */
object Backup {

    /** Profile-only exports stay crystaln/1; this is the superset. */
    fun exportFull(context: Context): String = JSONObject()
        .put("format", "crystaln/2")
        .put("selected", ProfileStore.selectedId ?: "")
        .put(
            "profiles",
            JSONArray().apply { ProfileStore.load(context).forEach { put(it.toJson()) } },
        )
        .put("subscriptions", SubscriptionsStore.exportJson(context))
        .put("settings", SettingsStore.exportJson(context))
        .toString(2)

    /**
     * Imports a crystaln/2 backup: settings first, then subscriptions, then
     * profiles (via the normal merge — re-imports update in place). Returns
     * a user-facing summary, or null when the text is not a crystaln/2 backup
     * (caller falls back to the profile-only import path).
     */
    fun importFull(context: Context, text: String): String? {
        val root = try {
            JSONObject(text.trim())
        } catch (_: Exception) {
            return null
        }
        if (root.optString("format") != "crystaln/2") return null

        root.optJSONObject("settings")?.let { SettingsStore.importJson(context, it) }
        val subs = root.optJSONArray("subscriptions")
            ?.let { SubscriptionsStore.importJson(context, it) } ?: 0

        // Profiles ride the existing crystaln/1 merge path (dedupe + selection).
        val parsed = ProfileImporter.parseText(text)
        val merged = if (parsed.isNotEmpty()) {
            ProfileStore.mergeProfiles(context, parsed, subscriptionId = null, selectFirst = true)
        } else null

        return buildString {
            if (merged != null) {
                append(context.getString(R.string.backup_profiles, merged.added + merged.updated))
            }
            if (subs > 0) {
                if (isNotEmpty()) append(" · ")
                append(context.getString(R.string.backup_subscriptions, subs))
            }
            if (isEmpty()) append(context.getString(R.string.backup_settings_only))
        }
    }
}
