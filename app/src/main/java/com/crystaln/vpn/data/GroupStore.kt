package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * User-defined profile groups. A group is just a name: profiles reference it
 * by [Profile.group], the canonical (non-empty) list lives here so empty
 * groups survive and can be filled later. Deleting a group unassigns its
 * profiles back to "no group".
 */
object GroupStore {
    private val lock = Any()
    private const val FILE = "groups.json"

    /** Loads the group names, sorted alphabetically. */
    fun load(context: Context): List<String> = synchronized(lock) {
        try {
            val f = File(context.filesDir, FILE)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val name = arr.optString(i)
                    if (name.isNotEmpty()) add(name)
                }
            }.sorted()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /** Adds a group; returns false when a group with this name already exists. */
    fun add(context: Context, name: String): Boolean = synchronized(lock) {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val list = File(context.filesDir, FILE).takeIf { it.exists() }
            ?.let { f -> try { JSONArray(f.readText()) } catch (_: Throwable) { JSONArray() } }
            ?: JSONArray()
        for (i in 0 until list.length()) {
            if (list.optString(i).equals(clean, ignoreCase = true)) return false
        }
        list.put(clean)
        persist(context, list)
        true
    }

    /** Deletes the group and unassigns all profiles that pointed to it. */
    fun remove(context: Context, name: String) {
        synchronized(lock) {
            val f = File(context.filesDir, FILE)
            val arr = f.takeIf { it.exists() }
                ?.let { try { JSONArray(it.readText()) } catch (_: Throwable) { JSONArray() } }
                ?: return
            val kept = JSONArray()
            for (i in 0 until arr.length()) {
                val n = arr.optString(i)
                if (n.isNotEmpty() && !n.equals(name, ignoreCase = true)) kept.put(n)
            }
            persist(context, kept)
        }
        // Clear the field from profiles that referenced this group.
        val list = ProfileStore.load(context)
        var changed = false
        list.forEach { p ->
            if (p.group?.equals(name, ignoreCase = true) == true) {
                p.group = null
                changed = true
            }
        }
        if (changed) ProfileStore.save(context)
    }

    private fun persist(context: Context, arr: JSONArray) {
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        val dst = File(context.filesDir, FILE)
        if (!tmp.renameTo(dst)) {
            dst.writeText(arr.toString())
            tmp.delete()
        }
    }
}
