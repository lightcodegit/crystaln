package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.data.Profile
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
 * - AmneziaWG: not supported on desktop yet (needs the amnezia kernel module).
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
        /** "tun" | "proxy" | "wg" | "openvpn" — backend actually used. */
        val mode: String = "",
        val error: String? = null,
        /** Session byte counters (0 when the backend has no visible device). */
        val rx: Long = 0,
        val tx: Long = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val lock = Any()

    private var xray: ChildProcess? = null
    private var tun2socks: ChildProcess? = null
    private var openvpn: ChildProcess? = null

    /** Extra default-route bookkeeping for the TUN teardown. */
    @Volatile
    private var origGateway: String? = null

    @Volatile
    private var wgConfPath: String? = null

    /** Network interface whose /sys counters feed the live traffic stats. */
    @Volatile
    private var statDevice: String? = null

    /** True while the 1 Hz traffic poller thread is running. */
    @Volatile
    private var polling = false

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
        synchronized(lock) {
            if (_ui.value.state == State.STARTING || _ui.value.state == State.CONNECTED) return
            generation++
            val myGen = generation
            _ui.value = UiState(State.STARTING, profile, error = null)
        }
        Thread {
            try {
                when {
                    profile.protocol == Protocol.WIREGUARD -> connectWireGuard(profile)
                    profile.protocol == Protocol.AMNEZIAWG -> throw IllegalStateException(
                        "AmneziaWG пока не поддерживается в Linux-версии (нужен модуль ядра amnezia)",
                    )
                    profile.protocol == Protocol.OPENVPN -> connectOpenVpn(profile)
                    else -> connectXray(profile)
                }
            } catch (t: Throwable) {
                val myGen = generation
                fail(t.message ?: t.javaClass.simpleName, myGen)
            }
        }.apply { name = "crystaln-connect"; isDaemon = true }.start()
    }

    private fun connectXray(p: Profile) {
        val dataDir = AppDirs.configDir()
        val socksPort = SettingsStore.socksPort(dataDir)
        val httpPort = SettingsStore.httpPort(dataDir)
        val opts = XrayConfigBuilder.Options(
            dnsViaTunnel = SettingsStore.dnsViaTunnel(dataDir),
            dnsServer = SettingsStore.dnsServer(dataDir),
            blockAds = SettingsStore.blockAds(dataDir),
        )
        val bin = AppDirs.binDir()
        val st = Binaries.status()
        if (!st.xrayReady) {
            throw IllegalStateException("Бинарник xray не найден — нажмите «Обновить бинарники»")
        }
        if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN && !st.tun2socksReady) {
            throw IllegalStateException("tun2socks не найден — нажмите «Обновить бинарники»")
        }
        if (opts.blockAds && !st.geositeReady) {
            logLine("geosite.dat отсутствует — блокировка рекламы выключена в этой сессии")
        }

        val config = XrayConfigBuilder.buildProxy(p, opts, socksPort, httpPort)
        val configFile = File(AppDirs.dataDir(), "xray-config.json")
        configFile.writeText(config)

        logLine("Запуск xray (${if (opts.blockAds) "с geosite" else "без geosite"})…")
        val env = mutableListOf("XRAY_LOCATION_ASSET=${bin.absolutePath}")
        xray = ChildProcess(
            "xray",
            listOf(ChildProcess.bin(bin, Binaries.XRAY), "run", "-c", configFile.absolutePath),
        ) { logLine("[xray] $it") }
        Thread.sleep(500)
        if (xray?.alive != true) {
            throw IllegalStateException("xray завершился сразу — смотрите журнал")
        }
        // Wait for the local socks listener; xray says nothing useful on stdout.
        var socksUp = false
        repeat(30) {
            socksUp = try {
                Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", socksPort), 300); true }
            } catch (_: Throwable) {
                Thread.sleep(200); false
            }
            if (socksUp) return@repeat
        }
        if (!socksUp) throw IllegalStateException("xray не открыл локальный прокси-порт")

        val mode = SettingsStore.tunnelMode(dataDir)
        if (mode == SettingsStore.TunnelMode.PROXY) {
            markConnected(p, "proxy")
            return
        }

        // ── TUN mode ─────────────────────────────────────────────────────
        logLine("Настройка TUN-устройства (нужны права root)…")
        origGateway = defaultGateway()
        val dev = "crystaln0"
        val setup = listOf(
            "ip tuntap add mode tun dev $dev",
            "ip addr add 198.18.0.1/15 dev $dev",
            "ip link set $dev up",
            "ip route replace default dev $dev",
        )
        val res = Privileges.runScript("tun-setup", setup)
        if (res != Privileges.Result.Ok) {
            teardownTunQuietly()
            xray?.stop()
            throw IllegalStateException(
                "Не удалось настроить TUN (нужны права root): " +
                    ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        tun2socks = ChildProcess(
            "tun2socks",
            listOf(
                ChildProcess.bin(bin, Binaries.TUN2SOCKS),
                "--device", dev,
                "--proxy", "socks5://127.0.0.1:$socksPort",
                "--loglevel", "warn",
            ),
        ) { logLine("[tun2socks] $it") }
        Thread.sleep(700)
        if (tun2socks?.alive != true) {
            teardownTunQuietly()
            xray?.stop()
            throw IllegalStateException("tun2socks завершился — смотрите журнал")
        }
        markConnected(p, "tun")
    }

    private fun connectWireGuard(p: Profile) {
        if (!ChildProcess.commandExists("wg-quick")) {
            throw IllegalStateException("wg-quick не установлен (пакет wireguard-tools)")
        }
        val conf = p.wgConfig.ifBlank {
            throw IllegalStateException("В профиле нет конфигурации WireGuard")
        }
        val name = "crystaln-${p.id.take(8)}"
        val path = "/etc/wireguard/$name.conf"
        logLine("Подключение wg-quick ($name)…")
        // wg-quick needs the config file inside /etc/wireguard with strict perms.
        val tmp = File.createTempFile(name, ".conf")
        tmp.writeText(conf)
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        val setup = listOf(
            "install -m 600 $tmp ${path.replace("\'", "\\'")}",
            "wg-quick up $name",
        )
        val res = Privileges.runScript("wg-up", setup)
        tmp.delete()
        if (res != Privileges.Result.Ok) {
            throw IllegalStateException(
                "wg-quick up не выполнен: " + ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        wgConfPath = path
        markConnected(p, "wg")
    }

    private fun connectOpenVpn(p: Profile) {
        if (!ChildProcess.commandExists("openvpn")) {
            throw IllegalStateException("openvpn не найден в системе")
        }
        val conf = p.ovpnConfig.ifBlank {
            throw IllegalStateException("В профиле нет конфигурации OpenVPN")
        }
        val file = File.createTempFile("crystaln-", ".ovpn")
        file.writeText(conf)
        logLine("Запуск openvpn…")
        // openvpn must run privileged to create the TUN; run it detached via
        // pkexec/sudo and supervise its lifecycle through a pidfile.
        val pidFile = File(AppDirs.dataDir(), "openvpn.pid")
        val script = listOf(
            "rm -f ${pidFile.absolutePath}",
            "sh -c 'openvpn --config ${file.absolutePath.replace("\'", "\\'")} " +
                "--dev crystaln0 --script-security 2 & echo \\$! > ${pidFile.absolutePath}'",
        )
        val res = Privileges.runScript("ovpn-up", script)
        if (res != Privileges.Result.Ok) {
            file.delete()
            throw IllegalStateException(
                "openvpn не запущен: " + ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        openvpn = ChildProcess(
            "openvpn-watch",
            listOf("sh", "-c", "while kill -0 $(cat ${pidFile.absolutePath}) 2>/dev/null; do sleep 1; done"),
        ) { /* watcher is silent; openvpn output went to the elevated session */ }
        markConnected(p, "openvpn")
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
            synchronized(lock) {
                if (generation == myGen) _ui.value = UiState(State.OFF)
            }
        }.apply { name = "crystaln-stop"; isDaemon = true }.start()
    }

    private fun teardownAll() {
        polling = false
        val mode = _ui.value.mode
        var stoppedSomething = false
        when (mode) {
            "wg" -> {
                wgConfPath?.let { path ->
                    val name = path.substringAfterLast('/').removeSuffix(".conf")
                    Privileges.runScript("wg-down", listOf("wg-quick down $name", "rm -f $path"))
                    stoppedSomething = true
                }
                wgConfPath = null
            }
            "openvpn" -> {
                val pidFile = File(AppDirs.dataDir(), "openvpn.pid")
                Privileges.runScript("ovpn-down", listOf("kill $(cat ${pidFile.absolutePath})", "rm -f ${pidFile.absolutePath}"))
                stoppedSomething = true
            }
            "tun" -> {
                tun2socks?.stop()
                tun2socks = null
                teardownTunQuietly()
                stoppedSomething = true
            }
        }
        if (xray != null) stoppedSomething = true
        xray?.stop()
        xray = null
        if (stoppedSomething) logLine("Туннель остановлен")
    }

    private fun teardownTunQuietly() {
        val dev = "crystaln0"
        val cmds = mutableListOf<String>()
        origGateway?.let { gw -> cmds += "ip route replace default via $gw" }
        cmds += "ip link del $dev"
        Privileges.runScript("tun-down", cmds)
        origGateway = null
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun markConnected(p: Profile, mode: String) {
        synchronized(lock) {
            generation.let { g ->
                if (_ui.value.state == State.STARTING) {
                    _ui.value = UiState(State.CONNECTED, p, mode, null)
                    logLine("Подключено (${describeMode(mode)})")
                }
            }
        }
        startTrafficPoller(p, mode)
    }

    /**
     * 1 Hz poller, the desktop equivalent of the Android VpnManager loop:
     * reads the kernel counters of the tunnel's network interface, feeds the
     * session rx/tx into [ui] and accumulates per-profile totals in
     * [TrafficStatsStore] (debounced — the file is written at most once a
     * minute plus once at session end). Proxy mode has no visible device, so
     * only the session duration accumulates there.
     */
    private fun startTrafficPoller(p: Profile, mode: String) {
        val dev = when (mode) {
            "tun" -> "crystaln0"
            "wg" -> "crystaln-${p.id.take(8)}"
            "openvpn" -> "crystaln0"
            else -> null // proxy: no network device to measure
        }
        statDevice = dev
        val dataDir = AppDirs.configDir()
        val myGen = generation
        polling = true
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
            }
            TrafficStatsStore.flush(dataDir)
        }.apply {
            name = "crystaln-traffic"
            isDaemon = true
            start()
        }
    }

    private fun deviceBytes(dev: String?, file: String): Long = try {
        File("/sys/class/net/$dev/statistics/$file").readText().trim().toLong()
    } catch (_: Throwable) {
        0L
    }

    private fun fail(message: String, myGen: Int) {
        synchronized(lock) {
            if (generation == myGen) {
                teardownAll()
                _ui.value = UiState(State.OFF, error = message)
                logLine("Ошибка: $message")
            }
        }
    }

    private fun describeMode(mode: String) = when (mode) {
        "tun" -> "TUN"
        "proxy" -> "локальный прокси"
        "wg" -> "WireGuard"
        "openvpn" -> "OpenVPN"
        else -> mode
    }

    private fun defaultGateway(): String? = try {
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
        } catch (_: Throwable) {
        }
        _ui.value = UiState(State.OFF)
    }
}
