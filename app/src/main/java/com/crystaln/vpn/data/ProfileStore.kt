package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Simple JSON-file backed profile storage in the app's private dir. */
object ProfileStore {
    private const val FILE = "profiles.json"

    @Volatile
    private var cache: MutableList<Profile>? = null

    @Volatile
    var selectedId: String? = null
        private set

    fun load(context: Context): MutableList<Profile> {
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
                // Corrupt store: start empty rather than crash the app.
            }
        }
        cache = list
        selectedId = selected?.takeIf { id -> list.any { it.id == id } }
        return list
    }

    fun save(context: Context) {
        val list = cache ?: return
        val root = JSONObject()
            .put("selected", selectedId ?: "")
            .put("profiles", JSONArray().apply { list.forEach { put(it.toJson()) } })
        val f = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(root.toString())
            tmp.delete()
        }
    }

    fun selected(context: Context): Profile? {
        val list = load(context)
        val id = selectedId
        return list.firstOrNull { it.id == id }
    }

    fun select(context: Context, id: String?) {
        selectedId = id
        save(context)
    }

    fun upsert(context: Context, profile: Profile) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == profile.id }
        if (idx >= 0) list[idx] = profile else list += profile
        if (selectedId == null) selectedId = profile.id
        save(context)
    }

    fun remove(context: Context, id: String) {
        val list = load(context)
        list.removeAll { it.id == id }
        if (selectedId == id) selectedId = list.firstOrNull()?.id
        save(context)
    }
}
