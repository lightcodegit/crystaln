package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * JSON-file backed profile storage in the app's private dir.
 *
 * Thread-safety: every access is guarded by [lock] — the store is touched from
 * the UI thread, IO coroutines and services, so a shared lock prevents
 * read-modify-write races and lost updates.
 */
object ProfileStore {
    private const val FILE = "profiles.json"

    private val lock = Any()

    @Volatile
    private var cache: MutableList<Profile>? = null

    @Volatile
    var selectedId: String? = null
        private set

    /** Test-only hook: clears the in-memory cache so a fresh context reloads from disk. */
    internal fun resetForTest() {
        synchronized(lock) {
            cache = null
            selectedId = null
        }
    }

    fun load(context: Context): MutableList<Profile> = synchronized(lock) {
        cache?.let { return it }
        val f = File(context.filesDir, FILE)
        val list = mutableListOf<Profile>()
        var selected: String? = null
        if (f.exists()) {
            try {
                val root = JSONObject(f.readText())
                selected = root.optString("selected").ifEmpty { null }
                val arr = root.optJSONArray("profiles") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    try {
                        list += Profile.fromJson(arr.getJSONObject(i))
                    } catch (_: Exception) {
                        // Skip a single malformed profile instead of discarding the whole store.
                    }
                }
            } catch (_: Exception) {
                // Corrupt store: keep the broken file for manual recovery instead
                // of letting a later save() wipe the user's profiles forever.
                try {
                    val backup = File(context.filesDir, "$FILE.corrupt")
                    backup.delete()
                    f.renameTo(backup)
                } catch (_: Exception) {
                }
            }
        }
        cache = list
        selectedId = selected?.takeIf { id -> list.any { it.id == id } }
        return list
    }

    fun save(context: Context): Unit = synchronized(lock) {
        val list = cache ?: return
        val root = JSONObject()
            .put("selected", selectedId ?: "")
            .put("profiles", JSONArray().apply { list.forEach { put(it.toJson()) } })
        val f = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        try {
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
        }
    }

    fun selected(context: Context): Profile? {
        val list = load(context)
        val id = selectedId
        return list.firstOrNull { it.id == id }
    }

    fun select(context: Context, id: String?) {
        synchronized(lock) {
            selectedId = id
            save(context)
        }
    }

    fun upsert(context: Context, profile: Profile) {
        synchronized(lock) {
            val list = load(context)
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list += profile
            if (selectedId == null) selectedId = profile.id
            save(context)
        }
    }

    fun remove(context: Context, id: String) {
        synchronized(lock) {
            val list = load(context)
            list.removeAll { it.id == id }
            if (selectedId == id) selectedId = list.firstOrNull()?.id
            save(context)
        }
    }

    data class MergeResult(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val selectedProfile: Profile?,
    )

    /**
     * Imports a batch as profiles, reusing an existing profile when it represents
     * the same server and credentials. This is what makes URL re-import and
     * subscription refreshes idempotent.
     *
     * Existing manual profiles are updated but never become subscription-owned,
     * so deleting a subscription will not delete a server the user had before.
     */
    fun mergeProfiles(
        context: Context,
        incoming: List<Profile>,
        subscriptionId: String?,
        selectFirst: Boolean = false,
    ): MergeResult = synchronized(lock) {
        val list = load(context)
        val beforeCount = list.size
        var added = 0
        var updated = 0
        val touched = mutableListOf<Profile>()
        incoming.forEach { fresh ->
            val key = fresh.identityKey() ?: fresh.id // fallback: deduplicate only exact ids
            val idx = list.indexOfFirst { it.id == fresh.id || it.identityKey() == key }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = fresh.copy(
                    id = old.id,
                    subscriptionId = old.subscriptionId,
                    // The user's pin survives subscription refreshes.
                    favorite = old.favorite,
                )
                updated++
                touched += list[idx]
            } else {
                val owned = fresh.copy(subscriptionId = subscriptionId)
                list += owned
                added++
                touched += owned
            }
        }

        if (subscriptionId != null) {
            val incomingKeys = incoming.mapNotNull { it.identityKey() }.toSet()
            val incomingIds = incoming.map { it.id }.toSet()
            val oldSize = list.size
            list.removeAll { p ->
                p.subscriptionId == subscriptionId &&
                    (p.identityKey()?.let { it !in incomingKeys } ?: (p.id !in incomingIds))
            }
            if (list.size < oldSize) touched += list.filter { it.subscriptionId == subscriptionId }
        }

        selectedId = selectedId?.takeIf { id -> list.any { it.id == id } }
        if (selectFirst) {
            selectedId = touched.firstOrNull()?.id ?: selectedId
        } else if (selectedId == null) {
            selectedId = list.firstOrNull()?.id
        }
        save(context)
        MergeResult(
            added = added,
            updated = updated,
            removed = beforeCount - list.size,
            selectedProfile = selectedId?.let { id -> list.firstOrNull { it.id == id } },
        )
    }

    /** Removes every profile owned by [subscriptionId]. */
    fun removeBySubscription(context: Context, subscriptionId: String) {
        synchronized(lock) {
            val list = load(context)
            list.removeAll { it.subscriptionId == subscriptionId }
            if (selectedId != null && list.none { it.id == selectedId }) {
                selectedId = list.firstOrNull()?.id
            }
        }
        save(context)
    }
}
