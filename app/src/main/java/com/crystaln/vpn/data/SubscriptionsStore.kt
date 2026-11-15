package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** A saved remote subscription URL that periodically refreshes profiles. */
data class Subscription(
    val id: String,
    val url: String,
    val name: String,
    val enabled: Boolean = true,
    val autoRefresh: Boolean = true,
    /** Wall-clock millis; 0 means it has never been refreshed successfully. */
    val lastUpdated: Long = 0,
    /** Last refresh result: null=never, true=ok, false=failed. */
    val lastResult: Boolean? = null,
    val lastError: String? = null,
    /** Refresh interval for background WorkManager scheduling. */
    val intervalHours: Int = 6,
)

/**
 * JSON-file backed subscription list. Separated from [ProfileStore] because a
 * subscription is metadata; profiles are the actual servers it imports.
 */
object SubscriptionsStore {
    private const val FILE = "subscriptions.json"
    private val lock = Any()

    @Volatile
    private var cache: MutableList<Subscription>? = null

    /** Test-only hook: clears the in-memory cache so a fresh context reloads from disk. */
    internal fun resetForTest() {
        synchronized(lock) {
            cache = null
        }
    }

    fun load(context: Context): List<Subscription> = synchronized(lock) {
        cache?.toList() ?: run {
            val list = mutableListOf<Subscription>()
            val f = File(context.filesDir, FILE)
            if (f.exists()) {
                try {
                    val root = JSONObject(f.readText())
                    val arr = root.optJSONArray("subscriptions") ?: JSONArray()
                    for (i in 0 until arr.length()) list += fromJson(arr.getJSONObject(i))
                } catch (_: Exception) {
                    // Same corrupt-file policy as profiles: preserve for manual
                    // recovery rather than overwrite on the next save.
                    try {
                        f.renameTo(File(context.filesDir, "$FILE.corrupt"))
                    } catch (_: Exception) {
                    }
                }
            }
            cache = list
            list.toList()
        }
    }

    fun byId(context: Context, id: String): Subscription? =
        load(context).firstOrNull { it.id == id }

    /** Adds or replaces a subscription with the same normalized URL. */
    fun add(context: Context, rawUrl: String): Subscription = synchronized(lock) {
        val url = normalizeUrl(rawUrl) ?: throw IllegalArgumentException("invalid url")
        val list = load(context).toMutableList()
        val existing = list.firstOrNull { it.url.equals(url, ignoreCase = true) }
        val sub = if (existing != null) {
            existing.copy(
                url = url,
                name = displayName(url),
                enabled = true,
            )
        } else {
            Subscription(
                id = java.util.UUID.randomUUID().toString(),
                url = url,
                name = displayName(url),
            )
        }
        val idx = list.indexOfFirst { it.id == sub.id }
        if (idx >= 0) list[idx] = sub else list += sub
        cache = list
        save(context)
        sub
    }

    fun update(context: Context, subscription: Subscription): Unit = synchronized(lock) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == subscription.id }
        if (idx >= 0) {
            list[idx] = subscription
            cache = list
            save(context)
        }
    }

    fun remove(context: Context, id: String): Unit = synchronized(lock) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == id }
        cache = list
        save(context)
    }

    fun setStatus(
        context: Context,
        id: String,
        success: Boolean,
        error: String? = null,
    ): Unit = synchronized(lock) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(
                lastUpdated = System.currentTimeMillis(),
                lastResult = success,
                lastError = if (success) null else error,
            )
            cache = list
            save(context)
        }
    }

    fun normalizeUrl(raw: String): String? = try {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host
        if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
            raw.trim()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    fun displayName(url: String): String = try {
        URI(url).host ?: url
    } catch (_: Exception) {
        url
    }

    private fun fromJson(o: JSONObject): Subscription = Subscription(
        id = o.optString("id"),
        url = o.optString("url"),
        name = o.optString("name"),
        enabled = o.optBoolean("enabled", true),
        autoRefresh = o.optBoolean("autoRefresh", true),
        lastUpdated = o.optLong("lastUpdated"),
        lastResult = if (o.isNull("lastResult")) null else o.optBoolean("lastResult"),
        lastError = o.optString("lastError").ifEmpty { null },
        intervalHours = o.optInt("intervalHours", 6).coerceIn(1, 30 * 24),
    )

    private fun save(context: Context): Unit {
        val list = cache ?: return
        val root = JSONObject().put(
            "subscriptions",
            JSONArray().apply { list.forEach { put(it.toJson()) } },
        )
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
}

private fun Subscription.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("url", url)
    put("name", name)
    put("enabled", enabled)
    put("autoRefresh", autoRefresh)
    put("lastUpdated", lastUpdated)
    put("lastResult", lastResult)
    put("lastError", lastError ?: "")
    put("intervalHours", intervalHours)
}
