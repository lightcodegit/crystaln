package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.desktop.AppDirs
import com.crystaln.vpn.data.TrafficStatsStore
import com.crystaln.vpn.xray.XrayConfigBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connection orchestrator for the desktop app. Starts/stops the right
 * backend for a profile:
 *
 * - Xray family: local xray-core child (SOCKS/HTTP inbounds). In TUN mode a
 *   privileged script wires a TUN device + default route to it through
 *   tun2socks; in proxy mode the user just points apps at the local proxy.
 * - WireGuard: native wg-quick up/down (needs root).
 * - AmneziaWG: userspace amneziawg-go + awg setconf (needs root), including
 *   junk parameters I1..I5 and wg-quick-style default routing through fwmark.
 * - OpenVPN: system openvpn child run with privileges.
 *
 * The JVM process owns the children, so closing the app tears everything
 * down (see [shutdownAll]).
 */
object TunnelManager {

    enum class State { OFF, STARTING, CONNECTED, STOPPING }

    data class UiState(
        val state: State = State.OFF,
        val profile: Profile? = null,
        /** "tun" | "proxy" | "wg" | "awg" | "openvpn" — backend actually used. */
        val mode: String = "",
        val error: String? = null,
        /** Session byte counters (0 when the backend has no visible device). */
        val rx: Long = 0,
        val tx: Long = 0,
        /** Transient warning shown on the main screen (monthly limit ...). */
        val notice: String? = null,
    )

    internal val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val lock = Any()

    internal var xray: ChildProcess? = null
    internal var tun2socks: ChildProcess? = null
    internal var openvpn: ChildProcess? = null

    /** Temporary OpenVPN configs and credentials, removed on disconnect/failure. */
    @Volatile
    internal var openvpnFiles: List<File> = emptyList()

    /** Windows: pid-файл detached openvpn (для ovpn-down). */
    @Volatile
    internal var openvpnPidFile: File? = null

    /** Extra default-route bookkeeping for the TUN teardown. */
    @Volatile
    internal var origGateway: String? = null

    @Volatile
    internal var wgConfPath: String? = null

    /** Windows: имя установленного amneziawg tunnel service (адаптера). */
    @Volatile
    internal var wgService: String? = null

    /** Windows: pid-файл detached tun2socks (убивается через хелпер). */
    @Volatile
    internal var tun2socksPidFile: File? = null

    /** Windows: IP сервера для bypass-маршрута при TUN teardown. */
    @Volatile
    internal var tunServerIp: String? = null

    /** Windows: индекс физического интерфейса основного маршрута. */
    @Volatile
    internal var origIfIndex: Int? = null

    /** Windows: файл "rx,tx" статов от PowerShell-поллера ([WindowsHelper]). */
    @Volatile
    private var statsFile: File? = null

    /** Kill-switch armed for the current TUN session; disengaged on clean stop. */
    @Volatile
    private var killArmed = false

    @Volatile
    internal var awgIface: String? = null

    /** Network interface whose /sys counters feed the live traffic stats. */
    @Volatile
    private var statDevice: String? = null

    /** True while the 1 Hz traffic poller thread is running. */
    @Volatile
    internal var polling = false

    private var generation = 0

    fun logLine(s: String) {
        Privileges.appendLog("crystaln.log", s + "\n")
        val list = _log.value.toMutableList()
        list.add(s)
        while (list.size > 250) list.removeAt(0)
        _log.value = list
    }

    // ── connect ──────────────────────────────────────────────────────────

    fun connect(profile: Profile) {
        val myGen: Int
        synchronized(lock) {
            // A user-initiated connect restarts the auto-reconnect ladder.
            autoReconnectProfileId = null
            autoReconnectSwitched = false
            autoReconnectAttempts = 0
            if (_ui.value.state == State.STARTING || _ui.value.state == State.CONNECTED) return
            generation++
            myGen = generation
            _ui.value = UiState(State.STARTING, profile, error = null)
        }
        Thread {
            try {
                when {
                    profile.protocol == Protocol.WIREGUARD -> connectWireGuard(profile)
                    profile.protocol == Protocol.AMNEZIAWG -> connectAmneziaWg(profile)
                    profile.protocol == Protocol.OPENVPN -> connectOpenVpn(profile)
                    profile.isSingBoxFamily -> connectSingBox(profile)
                    else -> connectXray(profile)
                }
            } catch (t: Throwable) {
                // Use the generation captured for THIS attempt — using the
                // current value would let a stale failure tear down a newer
                // connection.
                fail(t.message ?: t.javaClass.simpleName, myGen)
            }
        }.apply { name = "crystaln-connect"; isDaemon = true }.start()
    }


    internal fun requireDaemons() {
        if (!com.crystaln.vpn.desktop.Platform.supportsDaemons) {
            throw IllegalStateException(
                "WireGuard/AmneziaWG/OpenVPN пока не поддерживаются на " +
                    com.crystaln.vpn.desktop.Platform.displayName +
                    ". Используйте Xray-профиль (VLESS/VMess/Trojan/Shadowsocks/" +
                    "SOCKS/HTTP) — он работает в режиме локального прокси.",
            )
        }
    }


    // ── disconnect ───────────────────────────────────────────────────────

    fun stop() {
        val myGen: Int
        synchronized(lock) {
            if (_ui.value.state == State.OFF || _ui.value.state == State.STOPPING) return
            generation++
            myGen = generation
            _ui.value = _ui.value.copy(state = State.STOPPING)
        }
        Thread {
            teardownAll()
            if (killArmed) {
                KillSwitch.disengage()
                killArmed = false
            }
            synchronized(lock) {
                if (generation == myGen) _ui.value = UiState(State.OFF)
            }
        }.apply { name = "crystaln-stop"; isDaemon = true }.start()
    }


    // ── helpers ──────────────────────────────────────────────────────────

    internal fun markConnected(p: Profile, mode: String) {
        synchronized(lock) {
            generation.let { g ->
                if (_ui.value.state == State.STARTING) {
                    _ui.value = UiState(State.CONNECTED, p, mode, null)
                    logLine("Подключено (${describeMode(mode)})")
                }
            }
        }
        startTrafficPoller(p, mode)
        // Kill-switch: while the tunnel is up, only the VPN interface may
        // carry traffic. When it drops, the rule fails closed (no interface
        // matches) until the user stops/reconnects.
        if (SettingsStore.killSwitch(AppDirs.configDir()) && mode in setOf("tun", "wg", "awg", "openvpn")) {
            KillSwitch.engage(
                sessionId = "gen-${generation}",
                allowIface = if (!com.crystaln.vpn.desktop.Platform.isWindows) statDevice else null,
            )
            killArmed = true
            logLine("Kill-switch включён")
        }
    }

    /**
     * 1 Hz poller, the desktop equivalent of the Android VpnManager loop:
     * reads the kernel counters of the tunnel's network interface, feeds the
     * session rx/tx into [ui] and accumulates per-profile totals in
     * [TrafficStatsStore] (debounced — the file is written at most once a
     * minute plus once at session end). Proxy mode has no visible device, so
     * only the session duration accumulates there.
     */
    /**
     * Имя интерфейса ≤15 символов (IFNAMSIZ): "cn-<8>" даёт 17 и падает
     * в TUNSETIFF/wg-quick с EINVAL, поэтому префикс короткий.
     *
     * ВАЖНО: id профиля приходит из импортированного JSON и может содержать
     * произвольные символы. Имя интерфейса попадает в привилегированные
     * shell-скрипты — поэтому жёстко фильтруем его и добавляем хеш id,
     * чтобы разные профили не коллизировали при обрезании.
     */
    internal fun ifaceName(p: Profile): String {
        val safe = p.id.lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_') it else 'x' }
            .joinToString("")
            .take(8)
            .ifEmpty { "0" }
        return "cn-$safe"
    }

    private fun startTrafficPoller(p: Profile, mode: String) {
        val dev = when (mode) {
            "tun" -> "crystaln0"
            "wg", "awg" -> ifaceName(p)
            "openvpn" -> "crystaln0"
            else -> null // proxy: no network device to measure
        }
        statDevice = dev
        val dataDir = AppDirs.configDir()
        val myGen = generation
        polling = true
        statsFile = if (com.crystaln.vpn.desktop.Platform.isWindows && dev != null) {
            WindowsHelper.startStatsPoller(dev)
        } else {
            null
        }
        Thread {
            var lastRx = deviceBytes(dev, "rx_bytes")
            var lastTx = deviceBytes(dev, "tx_bytes")
            var ticks = 0
            while (polling && generation == myGen) {
                try { Thread.sleep(1_000) } catch (_: InterruptedException) { break }
                if (generation != myGen || !polling) break
                if (_ui.value.state != State.CONNECTED) break
                ticks++
                val rx = deviceBytes(dev, "rx_bytes")
                val tx = deviceBytes(dev, "tx_bytes")
                val drx = (rx - lastRx).coerceAtLeast(0)
                val dtx = (tx - lastTx).coerceAtLeast(0)
                lastRx = rx
                lastTx = tx
                synchronized(lock) {
                    if (generation == myGen && _ui.value.state == State.CONNECTED) {
                        val cur = _ui.value
                        _ui.value = cur.copy(rx = cur.rx + drx, tx = cur.tx + dtx)
                    }
                }
                TrafficStatsStore.add(dataDir, p.id, drx, dtx, 1)
                if (ticks % 30 == 0) updateTrafficLimitNotice(dataDir)
                // Watchdog: if our xray child died, the tunnel is gone while
                // the UI still says CONNECTED. stop() bumps the generation
                // first, so the loop can't race a clean disconnect; fail()
                // tears down, engages the kill-switch and runs the ladder.
                if (mode in setOf("tun", "proxy") && xray?.alive == false) {
                    polling = false
                    TrafficStatsStore.flush(dataDir)
                    fail("xray завершился — туннель потерян", myGen)
                    return@Thread
                }
            }
            TrafficStatsStore.flush(dataDir)
        }.apply {
            name = "crystaln-traffic"
            isDaemon = true
            start()
        }
    }

    private fun deviceBytes(dev: String?, file: String): Long = try {
        if (com.crystaln.vpn.desktop.Platform.isWindows) {
            // На Linux счётчики берутся из /sys/class/net/<dev>/statistics;
            // на Windows — из файла PowerShell-поллера Get-NetAdapterStatistics.
            val pair = WindowsHelper.readStatsFile(statsFile) ?: 0L to 0L
            if (file == "rx_bytes") pair.first else pair.second
        } else {
            File("/sys/class/net/$dev/statistics/$file").readText().trim().toLong()
        }
    } catch (_: Throwable) {
        0L
    }

    /**
     * Monthly-limit watchdog: every ~30 s compares the calendar-month total
     * with the configured cap and publishes a hero-banner notice at 80% /
     * 100% (mirrors Android's limitReached flag, which also never force-
     * disconnects — the provider's throttle, not our kill-switch).
     */
    private fun updateTrafficLimitNotice(dataDir: File) {
        val limitMb = SettingsStore.trafficLimitMb(dataDir)
        if (limitMb <= 0) return
        val used = TrafficStatsStore.monthTotal(dataDir).let { it.rx + it.tx }
        val pct = used * 100.0 / (limitMb * 1024.0 * 1024.0)
        val msg = when {
            pct >= 100.0 -> "Месячный лимит трафика исчерпан (${limitMb} МБ)"
            pct >= 80.0 -> "Использовано %.0f%% месячного лимита (%d МБ)".format(pct, limitMb)
            else -> return
        }
        synchronized(lock) {
            if (_ui.value.notice != msg) _ui.value = _ui.value.copy(notice = msg)
        }
        logLine(msg)
    }

    private fun fail(message: String, myGen: Int) {
        var failedProfile: Profile?
        synchronized(lock) {
            failedProfile = _ui.value.profile
            if (generation == myGen) {
                teardownAll()
                if (killArmed && com.crystaln.vpn.desktop.Platform.isWindows) {
                    KillSwitch.blockNow("gen-$myGen")
                    logLine("Kill-switch: трафик заблокирован после обрыва")
                }
                _ui.value = UiState(State.OFF, error = message)
                logLine("Ошибка: $message")
            }
        }
        maybeAutoReconnect(failedProfile)
    }

    private var autoReconnectAttempts = 0
    private var autoReconnectProfileId: String? = null
    private var autoReconnectSwitched = false

    /**
     * After a failure: up to 3 attempts on the same profile, then 3 more on
     * the fallback (explicit pick, else next in list — parity with Android's
     * VpnManager). A user-initiated connect resets the ladder.
     */
    private fun maybeAutoReconnect(failed: Profile?) {
        val dataDir = AppDirs.configDir()
        if (!SettingsStore.autoReconnect(dataDir)) return
        if (failed == null) return
        if (autoReconnectProfileId != failed.id) {
            autoReconnectProfileId = failed.id
            autoReconnectAttempts = 0
            autoReconnectSwitched = false
        }
        if (autoReconnectAttempts >= 3) {
            if (autoReconnectSwitched) return
            autoReconnectSwitched = true
            autoReconnectAttempts = 0
        }
        autoReconnectAttempts++
        val target = if (autoReconnectSwitched) resolveFallbackProfile(dataDir, failed) ?: failed else failed
        val myGen = generation
        Thread {
            Thread.sleep(5_000)
            synchronized(lock) {
                if (generation == myGen && _ui.value.state == State.OFF) {
                    logLine(
                        if (autoReconnectSwitched) "Автоподключение: переходим на fallback-профиль"
                        else "Автоподключение: попытка $autoReconnectAttempts из 3",
                    )
                }
            }
            if (_ui.value.state == State.OFF) connect(target)
        }.apply { isDaemon = true; name = "crystaln-reconnect" }.start()
    }

    private fun resolveFallbackProfile(dataDir: File, failed: Profile?): Profile? {
        val id = SettingsStore.fallbackProfileId(dataDir)
        if (id != null) {
            ProfileStore.load(dataDir).firstOrNull { it.id == id }?.let { return it }
        }
        val list = ProfileStore.load(dataDir)
        if (list.isEmpty()) return failed
        if (failed == null) return list.first()
        val idx = list.indexOfFirst { it.id == failed.id }
        if (idx < 0) return list.first()
        return list.getOrElse(idx + 1) { list.first() }
    }

    private fun describeMode(mode: String) = when (mode) {
        "tun" -> "TUN"
        "proxy" -> "локальный прокси"
        "wg" -> "WireGuard"
        "awg" -> "AmneziaWG"
        "openvpn" -> "OpenVPN"
        else -> mode
    }

    internal fun defaultGateway(): String? = try {
        val p = ProcessBuilder("ip", "route", "show", "default").start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        Regex("default via (\\S+)").find(out)?.groupValues?.get(1)
    } catch (_: Throwable) {
        null
    }

    /** JVM shutdown: make sure children die with the app. */
    fun shutdownAll() {
        try {
            polling = false
            teardownAll()
            if (killArmed) {
                KillSwitch.disengage()
                killArmed = false
            }
        } catch (_: Throwable) {
        }
        _ui.value = UiState(State.OFF)
    }
}
