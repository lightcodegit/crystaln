package com.crystaln.vpn.data

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
 * JSON-file backed per-profile usage totals. Desktop port of the Android
 * store: the 1 Hz poller in [com.crystaln.vpn.desktop.net.TunnelManager] adds
 * session deltas here; saves are debounced so the file is written at most
 * once a minute plus once on session end, never per tick.
 */
object TrafficStatsStore {
    private const val FILE = "traffic_stats.json"
    private const val SAVE_PERIOD_TICKS = 60 // 1 Hz poller → once a minute

    private val lock = Any()
    private val stats = HashMap<String, TrafficStats>()
    private var ticksSinceSave = 0
    private var loadedDir: File? = null

    /** Test-only hook: forgets the in-memory cache so a fresh dir reloads. */
    internal fun resetForTest() {
        synchronized(lock) {
            stats.clear()
            ticksSinceSave = 0
            loadedDir = null
        }
    }

    private fun loadIfNeeded(dataDir: File) {
        if (loadedDir == dataDir) return
        loadedDir = dataDir
        val f = File(dataDir, FILE)
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

    fun all(dataDir: File): Map<String, TrafficStats> = synchronized(lock) {
        loadIfNeeded(dataDir)
        stats.toMap()
    }

    /**
     * Adds byte counters for [profileId]; caller supplies the delta it
     * measured. Returns the accumulated total after the update.
     */
    fun add(
        dataDir: File,
        profileId: String,
        rx: Long,
        tx: Long,
        seconds: Long = 0,
    ): Unit = synchronized(lock) {
        if (rx <= 0 && tx <= 0 && seconds <= 0) return
        loadIfNeeded(dataDir)
        val old = stats[profileId] ?: TrafficStats()
        stats[profileId] = TrafficStats(
            rx = old.rx + rx,
            tx = old.tx + tx,
            seconds = old.seconds + seconds,
        )
        if (++ticksSinceSave >= SAVE_PERIOD_TICKS) save(dataDir)
    }

    fun get(dataDir: File, profileId: String): TrafficStats = synchronized(lock) {
        loadIfNeeded(dataDir)
        stats[profileId] ?: TrafficStats()
    }

    fun clear(dataDir: File, profileId: String): Unit = synchronized(lock) {
        loadIfNeeded(dataDir)
        stats.remove(profileId)
        save(dataDir)
    }

    /** Writes pending counters immediately (session end). */
    fun flush(dataDir: File): Unit = synchronized(lock) {
        loadIfNeeded(dataDir)
        save(dataDir)
    }

    private fun save(dataDir: File) {
        ticksSinceSave = 0
        val root = JSONObject()
        stats.forEach { (id, s) ->
            root.put(id, JSONObject().put("rx", s.rx).put("tx", s.tx).put("seconds", s.seconds))
        }
        dataDir.mkdirs()
        val f = File(dataDir, FILE)
        val tmp = File(dataDir, "$FILE.tmp")
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
