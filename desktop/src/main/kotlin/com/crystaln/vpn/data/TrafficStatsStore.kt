package com.crystaln.vpn.data

import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Accumulated usage counters for one profile. */
data class TrafficStats(
    val rx: Long = 0,
    val tx: Long = 0,
    /** Total seconds the tunnel ran with this profile. */
    val seconds: Long = 0,
)

/** Usage counters for one calendar day (all profiles summed). */
data class DailyTraffic(
    val date: LocalDate,
    val rx: Long = 0,
    val tx: Long = 0,
    val seconds: Long = 0,
)

/**
 * JSON-file backed per-profile usage totals plus a per-day history used by
 * the monthly traffic limit. Desktop port of the Android store: the 1 Hz
 * poller in [com.crystaln.vpn.desktop.net.TunnelManager] adds session deltas
 * here; saves are debounced so the file is written at most once a minute plus
 * once on session end, never per tick.
 */
object TrafficStatsStore {
    private const val FILE = "traffic_stats.json"
    private const val SAVE_PERIOD_TICKS = 60 // 1 Hz poller → once a minute
    private const val KEEP_DAYS = 90
    private val DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE

    private val lock = Any()
    private val stats = HashMap<String, TrafficStats>()
    private val daily = HashMap<String, DailyTraffic>()
    private var ticksSinceSave = 0
    private var loadedDir: File? = null

    /** Test-only hook: forgets the in-memory cache so a fresh dir reloads. */
    internal fun resetForTest() {
        synchronized(lock) {
            stats.clear()
            daily.clear()
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
                if (id == "daily") {
                    val d = root.getJSONObject(id)
                    d.keys().forEach { day ->
                        val o = d.getJSONObject(day)
                        runCatching { LocalDate.parse(day, DAY_FMT) }.getOrNull()?.let { date ->
                            daily[day] = DailyTraffic(
                                date = date,
                                rx = o.optLong("rx"),
                                tx = o.optLong("tx"),
                                seconds = o.optLong("seconds"),
                            )
                        }
                    }
                } else {
                    val o = root.getJSONObject(id)
                    stats[id] = TrafficStats(
                        rx = o.optLong("rx"),
                        tx = o.optLong("tx"),
                        seconds = o.optLong("seconds"),
                    )
                }
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
        val key = LocalDate.now().format(DAY_FMT)
        val day = daily[key] ?: DailyTraffic(LocalDate.now())
        daily[key] = day.copy(rx = day.rx + rx, tx = day.tx + tx, seconds = day.seconds + seconds)
        if (++ticksSinceSave >= SAVE_PERIOD_TICKS) save(dataDir)
    }

    fun get(dataDir: File, profileId: String): TrafficStats = synchronized(lock) {
        loadIfNeeded(dataDir)
        stats[profileId] ?: TrafficStats()
    }

    /** Per-day history, newest first, capped at [KEEP_DAYS] entries. */
    fun dailyHistory(dataDir: File): List<DailyTraffic> = synchronized(lock) {
        loadIfNeeded(dataDir)
        daily.values.sortedByDescending { it.date }
    }

    /** Sum of the current calendar month (all profiles), for the limit check. */
    fun monthTotal(dataDir: File): TrafficStats = synchronized(lock) {
        loadIfNeeded(dataDir)
        val month = LocalDate.now().withDayOfMonth(1).toString()
        var rx = 0L
        var tx = 0L
        var sec = 0L
        daily.values.forEach { d ->
            if (d.date.withDayOfMonth(1).toString() == month) {
                rx += d.rx
                tx += d.tx
                sec += d.seconds
            }
        }
        TrafficStats(rx = rx, tx = tx, seconds = sec)
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
        // Keep the history bounded: drop days older than KEEP_DAYS.
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS.toLong()).format(DAY_FMT)
        daily.keys.filter { it < cutoff }.forEach { daily.remove(it) }
        val d = JSONObject()
        daily.forEach { (day, s) ->
            d.put(day, JSONObject().put("rx", s.rx).put("tx", s.tx).put("seconds", s.seconds))
        }
        root.put("daily", d)
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
