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
                for (i in 0 until arr.length()) list += Profile.fromJson(arr.getJSONObject(i))
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
        }
        save(context)
    }

    fun upsert(context: Context, profile: Profile) {
        synchronized(lock) {
            val list = load(context)
            val idx = list.indexOfFirst { it.id == profile.id }
            if (idx >= 0) list[idx] = profile else list += profile
            if (selectedId == null) selectedId = profile.id
        }
        save(context)
    }

    fun remove(context: Context, id: String) {
        synchronized(lock) {
            val list = load(context)
            list.removeAll { it.id == id }
            if (selectedId == id) selectedId = list.firstOrNull()?.id
        }
        save(context)
    }
}
