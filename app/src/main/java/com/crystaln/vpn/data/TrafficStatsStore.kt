package com.crystaln.vpn.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Accumulated usage counters for one profile. */
data class TrafficStats(
    val rx: Long = 0,
    val tx: Long = 0,
    /** Total seconds the tunnel ran with this profile. */
    val seconds: Long = 0,
)

/**
 * JSON-file backed per-profile usage totals. The tunnel's 1 Hz poller adds
 * session deltas here; saves are debounced so the file is written at most
 * once a minute plus once on session end, never per tick.
 */
object TrafficStatsStore {
    private const val FILE = "traffic_stats.json"
    private const val SAVE_PERIOD_TICKS = 60 // 1 Hz poller → once a minute

    private val lock = Any()
    private val stats = HashMap<String, TrafficStats>()
    private var ticksSinceSave = 0
    private var loaded = false

    /** Test-only hook: forgets the in-memory cache so a fresh context reloads. */
    internal fun resetForTest() {
        synchronized(lock) {
            stats.clear()
            loaded = false
            ticksSinceSave = 0
        }
    }

    private fun loadIfNeeded(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                stats[id] = TrafficStats(
                    rx = o.optLong("rx"),
                    tx = o.optLong("tx"),
                    seconds = o.optLong("seconds"),
                )
            }
        } catch (_: Exception) {
            // Corrupt stats are disposable usage counters, not user data.
        }
    }

    fun all(context: Context): Map<String, TrafficStats> = synchronized(lock) {
        loadIfNeeded(context)
        stats.toMap()
    }

    /**
     * Adds byte counters for [profileId]; caller supplies the delta it
     * measured. Returns the accumulated total after the update.
     */
    fun add(
        context: Context,
        profileId: String,
        rx: Long,
        tx: Long,
        seconds: Long = 0,
    ): Unit = synchronized(lock) {
        if (rx <= 0 && tx <= 0 && seconds <= 0) return
        loadIfNeeded(context)
        val old = stats[profileId] ?: TrafficStats()
        stats[profileId] = TrafficStats(
            rx = old.rx + rx,
            tx = old.tx + tx,
            seconds = old.seconds + seconds,
        )
        if (++ticksSinceSave >= SAVE_PERIOD_TICKS) save(context)
    }

    fun get(context: Context, profileId: String): TrafficStats = synchronized(lock) {
        loadIfNeeded(context)
        stats[profileId] ?: TrafficStats()
    }

    fun clear(context: Context, profileId: String): Unit = synchronized(lock) {
        loadIfNeeded(context)
        stats.remove(profileId)
        save(context)
    }

    /** Writes pending counters immediately (session end). */
    fun flush(context: Context): Unit = synchronized(lock) {
        loadIfNeeded(context)
        save(context)
    }

    private fun save(context: Context) {
        ticksSinceSave = 0
        val root = JSONObject()
        stats.forEach { (id, s) ->
            root.put(id, JSONObject().put("rx", s.rx).put("tx", s.tx).put("seconds", s.seconds))
        }
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
