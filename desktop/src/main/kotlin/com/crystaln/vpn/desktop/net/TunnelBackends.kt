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

/** Active connectivity check: HTTP through the local socks inbound. */
internal fun probeThroughTunnel(url: String, socksPort: Int, timeoutSec: Int): Boolean {
    val deadline = System.currentTimeMillis() + timeoutSec * 1000L
    while (System.currentTimeMillis() < deadline) {
        try {
            val conn = java.net.URL(url).openConnection(
                java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", socksPort)),
            ) as java.net.HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..399) return true
        } catch (_: Throwable) {
        }
        Thread.sleep(600)
    }
    return false
}

internal fun TunnelManager.connectXray(p: Profile) {
        val dataDir = AppDirs.configDir()
        val socksPort = SettingsStore.socksPort(dataDir)
        val httpPort = SettingsStore.httpPort(dataDir)
        val opts = XrayConfigBuilder.Options(
            dnsViaTunnel = SettingsStore.dnsViaTunnel(dataDir),
            dnsServer = SettingsStore.dnsServer(dataDir),
            blockAds = SettingsStore.blockAds(dataDir),
            splitTunnelMode = SettingsStore.splitTunnelMode(dataDir),
            splitTunnelRules = SettingsStore.splitTunnelRules(dataDir),
        )
        val bin = AppDirs.binDir()
        val st = Binaries.status()
        if (!st.xrayReady) {
            throw IllegalStateException("Бинарник xray не найден — нажмите «Обновить бинарники»")
        }
        if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN &&
            !com.crystaln.vpn.desktop.Platform.supportsTun
        ) {
            throw IllegalStateException(
                "TUN-режим недоступен на " + com.crystaln.vpn.desktop.Platform.displayName +
                    ": переключите режим в «Настройки → Прокси» (SOCKS/HTTP через xray).",
            )
        }
        if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN && !st.tun2socksReady) {
            throw IllegalStateException("tun2socks не найден — нажмите «Обновить бинарники»")
        }
        if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN && !st.wintunReady) {
            throw IllegalStateException("wintun.dll не найден — нажмите «Обновить бинарники»")
        }
        if (opts.blockAds && !st.geositeReady) {
            logLine("geosite.dat отсутствует — блокировка рекламы выключена в этой сессии")
        }

        val config = XrayConfigBuilder.buildProxy(p, opts, socksPort, httpPort)
        val configFile = File(AppDirs.dataDir(), "xray-config.json")
        SecureFiles.writeText(configFile, config)

        // Hysteria2: the hysteria core (child process) must be up and serving
        // its local SOCKS before xray starts — its outbound points there.
        if (p.isHysteria) {
            HysteriaRunner.start(p)
        }
        if (p.isSingBoxFamily) {
            SingBoxRunner.start(p)
        }


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
        // PROXY mode: this app's own traffic never enters the tunnel (the
        // SOCKS inbound serves third-party apps), so an active check here
        // would only produce false failures — the check runs in TUN mode.
        if (mode == SettingsStore.TunnelMode.PROXY) {
            markConnected(p, "proxy")
            return
        }

        // Active connectivity check through the tunnel.
        if (!probeThroughTunnel(SettingsStore.checkUrl(dataDir), socksPort, SettingsStore.checkTimeoutSec(dataDir))) {
            xray?.stop()
            throw IllegalStateException("Сервер не отвечает через туннель")
        }

        // ── TUN mode ─────────────────────────────────────────────────────
        if (com.crystaln.vpn.desktop.Platform.isWindows) {
            connectTunWindows(p, socksPort)
            return
        }

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

    // ── Windows backends ─────────────────────────────────────────────────

    /**
     * Windows TUN: tun2socks создаёт wintun-адаптер сам, поэтому он стартует
     * первым (elevated, detached), затем адаптеру назначается IP 198.18.0.1/15
     * и добавляются маршруты. IP VPN-сервера получает bypass-маршрут через
     * оригинальный шлюз — иначе туннель «проглатывает» собственный
     * транспортный трафик (анти-слуп).
     */
internal fun TunnelManager.connectTunWindows(p: Profile, socksPort: Int) {
        val bin = AppDirs.binDir()
        val exe = ChildProcess.bin(bin, Binaries.TUN2SOCKS)
        val pidFile = File(AppDirs.dataDir(), "tun2socks.pid")
        val dev = "crystaln0"
        val gw = defaultGatewayWindows()
        val serverIp = resolveAddress(p.address)
        logLine("Настройка TUN-устройства (нужны права администратора)…")
        val cmds = mutableListOf(
            // Start-Process не задаёт $LASTEXITCODE (cmdlet) — ок для прелюдии.
            "\$t2s = Start-Process -FilePath '$exe' -ArgumentList " +
                "'-device','$dev','-proxy','socks5://127.0.0.1:$socksPort','-loglevel','warn' " +
                "-WindowStyle Hidden -PassThru; \$t2s.Id | Set-Content '${pidFile.absolutePath}'",
            waitForAdapterCommand(dev, tries = 60, pauseMs = 500),
            "netsh interface ip set address name=\"$dev\" source=static " +
                "addr=198.18.0.1 mask=255.254.0.0",
        )
        if (serverIp != null && gw != null) {
            cmds += "route add $serverIp mask 255.255.255.255 ${gw.first} metric 1 if ${gw.second}"
        } else {
            logLine("Не удалось определить IP сервера или шлюз — bypass-маршрут не добавлен")
        }
        cmds += "route add 0.0.0.0 mask 0.0.0.0 198.18.0.1 metric 1"
        val res = Privileges.runScript("tun-setup-win", cmds)
        if (res != Privileges.Result.Ok) {
            // tun2socks уже мог успеть стартовать — тихо убираем.
            Privileges.runScript("tun-cleanup-win", listOf(killPidCommand(pidFile)))
            throw IllegalStateException(
                "Не удалось настроить TUN (нужны права администратора): " +
                    ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        tun2socksPidFile = pidFile
        tunServerIp = serverIp
        origGateway = gw?.first
        origIfIndex = gw?.second
        markConnected(p, "tun")
    }

    /**
     * WireGuard/AWG на Windows: amneziawg.exe устанавливает туннель как
     * Windows-сервис (SYSTEM). Сервис сам создаёт wintun-адаптер, применяет
     * Address/DNS/MTU (wg-quick-семантика) и маршруты AllowedIPs, включая
     * bypass-маршрут до endpoint. Один бинарник обслуживает и plain WireGuard,
     * и AmneziaWG (junk-параметры I1..I5).
     */
internal fun TunnelManager.connectWgService(p: Profile, mode: String) {
        if (p.wgConfig.isBlank()) {
            throw IllegalStateException(
                "В профиле нет конфигурации ${if (mode == "awg") "AmneziaWG" else "WireGuard"}",
            )
        }
        if (!Binaries.status().awgReady) {
            throw IllegalStateException(
                "Бинарники AmneziaWG не найдены — Настройки → Система → «Обновить бинарники»",
            )
        }
        val parsed = WgSetconf.build(p.wgConfig, p.fields)
        val iface = ifaceName(p)
        val confFile = File(File(AppDirs.dataDir(), "tunnels"), "$iface.conf")
        confFile.parentFile?.mkdirs()
        SecureFiles.writeText(confFile, buildServiceConf(parsed))
        val exe = File(AppDirs.binDir(), "${Binaries.AWG_SERVICE}.exe").absolutePath
        logLine("Подключение ${if (mode == "awg") "AmneziaWG" else "WireGuard"} ($iface)…")
        val cmds = listOf(
            // Фоновая чистка застрявшего старого сервиса; не должна фейлить скрипт.
            "& '$exe' /uninstalltunnelservice $iface | Out-Null; \$LASTEXITCODE = 0",
            "& '$exe' /installtunnelservice '${confFile.absolutePath}'",
            // Сервис появляется асинхронно: ждём адаптер с именем туннеля.
            waitForAdapterCommand(iface, tries = 150, pauseMs = 400),
        )
        val res = Privileges.runScript("wg-service-up", cmds)
        if (res != Privileges.Result.Ok) {
            Privileges.runScript(
                "wg-service-cleanup",
                listOf("& '$exe' /uninstalltunnelservice $iface | Out-Null; \$LASTEXITCODE = 0"),
            )
            throw IllegalStateException(
                "Не удалось установить туннель $iface: " +
                    ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        wgService = iface
        markConnected(p, mode)
    }

    /**
     * wg-quick-style конфиг для amneziawg.exe /installtunnelservice: [Interface]
     * дополняется Address/DNS/MTU (их setconf-формат выносит отдельно).
     */
private fun buildServiceConf(parsed: WgSetconf.Result): String {
        if (parsed.addresses.isEmpty()) {
            throw IllegalStateException("В конфигурации нет Address — адаптеру нечего назначить")
        }
        val head = "[Interface]\n"
        val rest = parsed.setconf.removePrefix(head)
        return buildString {
            append(head)
            append("Address = ").appendLine(parsed.addresses.joinToString(", "))
            if (parsed.dnsServers.isNotEmpty()) {
                append("DNS = ").appendLine(parsed.dnsServers.joinToString(", "))
            }
            append("MTU = ").appendLine(parsed.mtu.toString())
            append(rest)
        }
    }

internal fun TunnelManager.connectOpenVpnWindows(p: Profile) {
        val exe = Binaries.openVpnExecutable()
        if (!exe.exists()) {
            throw IllegalStateException(
                "openvpn не найден — Настройки → Система → «Обновить бинарники»",
            )
        }
        val conf = p.ovpnConfig.ifBlank {
            throw IllegalStateException("В профиле нет конфигурации OpenVPN")
        }
        val dir = File(AppDirs.dataDir(), "tunnels")
        dir.mkdirs()
        val base = "openvpn-" + p.id.lowercase().map {
            if (it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_') it else 'x'
        }.joinToString("").take(8).ifEmpty { "0" }
        val confFile = File(dir, "$base.ovpn").apply { writeText(conf) }
        val user = p.fields["ovpnUser"]?.trim().orEmpty()
        val pass = p.fields["ovpnPass"]?.trim().orEmpty()
        val authFile = openVpnCredentialText(user, pass)?.let {
            File(dir, "$base.auth").apply { SecureFiles.writeText(this, it) }
        }
        val logFile = File(dir, "$base.log")
        val pidFile = File(dir, "$base.pid")
        logLine("Запуск openvpn…")
        val args = mutableListOf(
            "--config", "\"${confFile.absolutePath}\"",
            "--dev", "crystaln0",
            "--windows-driver", "wintun",
            "--log", "\"${logFile.absolutePath}\"",
        )
        if (authFile != null) args += listOf("--auth-user-pass", "\"${authFile.absolutePath}\"")
        val cmds = listOf(
            "\$o = Start-Process -FilePath '${exe.absolutePath}' -ArgumentList " +
                args.joinToString(",") { "'$it'" } +
                " -WindowStyle Hidden -PassThru; \$o.Id | Set-Content '${pidFile.absolutePath}'",
            // Адаптер появляется при успешном подъёме туннеля.
            waitForAdapterCommand("crystaln0", tries = 100, pauseMs = 500),
            // Адаптер может остаться от прошлого подключения — проверяем сам
            // процесс: если он умер (ошибка аутентификации/конфига), показываем
            // его лог.
            "if (-not (Get-Process -Id (Get-Content '${pidFile.absolutePath}') " +
                "-ErrorAction SilentlyContinue)) { " +
                "Get-Content '${logFile.absolutePath}' -Tail 15 -ErrorAction SilentlyContinue; exit 1 }",
        )
        val res = Privileges.runScript("ovpn-up-win", cmds)
        if (res != Privileges.Result.Ok) {
            Privileges.runScript("ovpn-cleanup-win", listOf(killPidCommand(pidFile)))
            confFile.delete()
            authFile?.delete()
            logFile.delete()
            pidFile.delete()
            throw IllegalStateException(
                "openvpn не запущен: " +
                    ((res as Privileges.Result.Fail).output.ifBlank { "смотрите журнал" }),
            )
        }
        openvpnFiles = listOfNotNull(confFile, authFile, logFile, pidFile)
        openvpnPidFile = pidFile
        markConnected(p, "openvpn")
    }

    /** PowerShell-цикл ожидания wintun-адаптера; exit 1 при таймауте. */
private fun waitForAdapterCommand(name: String, tries: Int, pauseMs: Int): String =
        "\$ok=\$false; foreach(\$i in 1..$tries){ " +
            "if (Get-NetAdapter -Name '$name' -ErrorAction SilentlyContinue) { \$ok=\$true; break }; " +
            "Start-Sleep -Milliseconds $pauseMs }; if (-not \$ok) { exit 1 }"

    /** Убивает процесс по pid-файлу; строка не должна фейлить скрипт. */
private fun killPidCommand(pidFile: File): String =
        "if (Test-Path '${pidFile.absolutePath}') { " +
            "\$p = Get-Content '${pidFile.absolutePath}' -ErrorAction SilentlyContinue; " +
            "if (\$p) { Get-Process -Id \$p -ErrorAction SilentlyContinue | Stop-Process -Force }; " +
            "Remove-Item '${pidFile.absolutePath}' -Force -ErrorAction SilentlyContinue }"

    /** Шлюз и индекс интерфейса основного маршрута Windows, null если нет. */
private fun defaultGatewayWindows(): Pair<String, Int>? {
        return try {
            val ps = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "Get-NetRoute -DestinationPrefix 0.0.0.0/0 -ErrorAction SilentlyContinue | " +
                    "Sort-Object RouteMetric | Select-Object -First 1 | " +
                    "ForEach-Object { \"{0}|{1}\" -f \$_.NextHop, \$_.InterfaceIndex }",
            ).apply { redirectErrorStream(true) }.start()
            val out = ps.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            ps.waitFor()
            val line = out.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            val parts = line.split('|')
            if (parts.size != 2) return null
            val gw = parts[0].trim()
            val idx = parts[1].trim().toIntOrNull()
            // NextHop 0.0.0.0 = on-link маршрут, через него bypass не построить.
            if (idx != null && gw.isNotEmpty() && gw != "0.0.0.0") gw to idx else null
        } catch (_: Throwable) {
            null
        }
    }

    /** Резолвит адрес сервера (для bypass-маршрута); null при неудаче. */
private fun resolveAddress(host: String?): String? = try {
        host?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { java.net.InetAddress.getAllByName(it).firstOrNull()?.hostAddress }
    } catch (_: Throwable) {
        null
    }

internal fun TunnelManager.connectWireGuard(p: Profile) {
        if (com.crystaln.vpn.desktop.Platform.isWindows) {
            connectWgService(p, "wg")
            return
        }
        requireDaemons()
        if (!ChildProcess.commandExists("wg-quick")) {
            if (Binaries.status().awgReady) {
                logLine("wg-quick не установлен — использую amneziawg-go в режиме WireGuard…")
                connectAwgBackend(p, "wg")
                return
            }
            throw IllegalStateException(
                "wg-quick не установлен (пакет wireguard-tools) и нет amneziawg-go — нажмите «Обновить бинарники»",
            )
        }
        val conf = p.wgConfig.ifBlank {
            throw IllegalStateException("В профиле нет конфигурации WireGuard")
        }
        val name = ifaceName(p)
        val path = "/etc/wireguard/$name.conf"
        logLine("Подключение wg-quick ($name)…")
        // wg-quick needs the config file inside /etc/wireguard with strict perms.
        val tmp = File.createTempFile(name, ".conf")
        SecureFiles.writeText(tmp, conf)
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

internal fun TunnelManager.connectAmneziaWg(p: Profile) {
        requireDaemons()
        connectAwgBackend(p, "awg")
    }

    /** Userspace WireGuard-compatible backend used by AWG and as a wg-quick fallback. */
internal fun TunnelManager.connectAwgBackend(p: Profile, mode: String) {
        if (com.crystaln.vpn.desktop.Platform.isWindows) {
            // Windows: amneziawg.exe tunnel service, plain WG и AWG одним бинарем.
            connectWgService(p, mode)
            return
        }
        if (!Binaries.status().awgReady) {
            throw IllegalStateException(
                "Бинарники AmneziaWG не найдены — Настройки → Система → «Обновить бинарники»",
            )
        }
        if (p.wgConfig.isBlank()) {
            throw IllegalStateException("В профиле нет конфигурации ${if (mode == "awg") "AmneziaWG" else "WireGuard"}")
        }
        val parsed = WgSetconf.build(p.wgConfig, p.fields)
        val iface = ifaceName(p)
        val bin = AppDirs.binDir()
        val goBin = ChildProcess.bin(bin, Binaries.AWG_GO)
        val awgBin = ChildProcess.bin(bin, Binaries.AWG)
        val pidFile = File(AppDirs.dataDir(), "amneziawg.pid")
        val confFile = "/var/run/amneziawg/$iface.conf"
        val socket = "/var/run/amneziawg/$iface.sock"
        // Лог демона нужен для диагностики: без него причина падения
        // amneziawg-go невидима (вывод больше не идёт в /dev/null).
        val logFile = "/var/run/amneziawg/$iface.log"
        val tmp = File.createTempFile(iface, ".conf")
        SecureFiles.writeText(tmp, parsed.setconf)
        tmp.setReadable(false, false)
        tmp.setReadable(true, true)
        logLine("Запуск amneziawg-go ($iface)…")
        val cmds = mutableListOf(
            "mkdir -p /var/run/amneziawg",
            "rm -f $confFile $socket ${pidFile.absolutePath}",
            "install -m 600 ${tmp.absolutePath} $confFile",
            // -f is important: without it amneziawg-go re-execs itself and $!
            // would point at the short-lived parent, not the actual daemon.
            // CRYSTALN_DEBUG=1 включает подробный лог amneziawg-go (диагностика
            // хендшейков). По умолчанию — только ошибки, чтобы лог не рос.
            "LOG_LEVEL=\${CRYSTALN_DEBUG:+debug} nohup $goBin -f $iface >\"$logFile\" 2>&1 & echo \$! > ${pidFile.absolutePath}",
            "for i in $(seq 1 100); do [ -S $socket ] && break; sleep 0.1; done",
            // Если сокет не появился — процесс мёртв или завис; показываем его лог,
            // иначе пользователь видит только вторичную ошибку kill.
            "[ -S $socket ] || { kill $(cat ${pidFile.absolutePath}) 2>/dev/null; tail -n 10 \"$logFile\" 2>/dev/null; exit 1; }",
            "$awgBin setconf $iface $confFile",
            "ip link set $iface mtu ${parsed.mtu} up",
        )
        parsed.addresses.forEach { cmds += "ip address add $it dev $iface" }
        if (parsed.defaultRoute) {
            // Same routing model as wg-quick: tunnel packets for ordinary
            // traffic, but leave the server's route in the main table.
            cmds += listOf(
                "$awgBin set $iface fwmark 51820",
                "ip -4 rule add not fwmark 51820 table 51820 || true",
                "ip -4 rule add table main suppress_prefixlength 0 || true",
                "ip -6 rule add not fwmark 51820 table 51820 || true",
                "ip -6 rule add table main suppress_prefixlength 0 || true",
            )
            if ("0.0.0.0/0" in parsed.routes) {
                cmds += "ip -4 route add 0.0.0.0/0 dev $iface table 51820 || true"
            }
            if ("::/0" in parsed.routes) {
                cmds += "ip -6 route add ::/0 dev $iface table 51820 || true"
            }
        }
        parsed.routes.filter { it != "0.0.0.0/0" && it != "::/0" }.forEach {
            cmds += "ip route replace $it dev $iface"
        }
        if (parsed.dnsServers.isNotEmpty()) {
            // Все запросы (включая заблокированные домены, которые провайдер
            // подменяет) должны идти в DNS туннеля, а не в DNS роутера.
            cmds += "resolvectl dns $iface ${parsed.dnsServers.joinToString(" ")} || true"
            cmds += "resolvectl domain $iface '~.' || true"
            cmds += "resolvectl default-route $iface yes || true"
            cmds += "resolvectl flush-caches || true"
        }
        val res = Privileges.runScript("awg-up", cmds)
        tmp.delete()
        if (res != Privileges.Result.Ok) {
            Privileges.runScript("awg-failed", awgDownCommands(iface, pidFile, confFile, socket))
            throw IllegalStateException(
                "amneziawg-go не запущен: " +
                    ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        awgIface = iface
        markConnected(p, mode)
    }

private fun awgDownCommands(iface: String, pidFile: File, confFile: String, socket: String) = listOf(
        "kill $(cat ${pidFile.absolutePath}) || true",
        "rm -f ${pidFile.absolutePath}",
        "ip -4 rule del table main suppress_prefixlength 0 || true",
        "ip -4 rule del not fwmark 51820 table 51820 || true",
        "ip -6 rule del table main suppress_prefixlength 0 || true",
        "ip -6 rule del not fwmark 51820 table 51820 || true",
        "ip route flush table 51820 || true",
        "ip link del $iface || true",
        "resolvectl revert $iface || true",
        "rm -f $confFile $socket",
    )

internal fun TunnelManager.connectOpenVpn(p: Profile) {
        if (com.crystaln.vpn.desktop.Platform.isWindows) {
            connectOpenVpnWindows(p)
            return
        }
        requireDaemons()
        if (!ChildProcess.commandExists("openvpn")) {
            throw IllegalStateException("openvpn не найден в системе")
        }
        val conf = p.ovpnConfig.ifBlank {
            throw IllegalStateException("В профиле нет конфигурации OpenVPN")
        }
        val file = File.createTempFile("crystaln-", ".ovpn")
        SecureFiles.writeText(file, conf)
        // OpenVPN runs as root and reads these files before dropping privileges.
        // Keep secrets owner-readable on disk, even before pkexec starts it.
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        val user = p.fields["ovpnUser"]?.trim().orEmpty()
        val pass = p.fields["ovpnPass"]?.trim().orEmpty()
        val authFile = openVpnCredentialText(user, pass)?.let { credentials ->
            File.createTempFile("crystaln-", ".auth").apply {
                SecureFiles.writeText(this, credentials)
            }
        }
        logLine("Запуск openvpn…")
        // openvpn must run privileged to create the TUN; run it detached via
        // pkexec/sudo and supervise its lifecycle through a pidfile.
        val pidFile = File(AppDirs.dataDir(), "openvpn.pid")
        val script = listOf(
            "rm -f ${pidFile.absolutePath}",
            "sh -c 'openvpn --config ${file.absolutePath.replace("\'", "\\'")} " +
                (authFile?.let { "--auth-user-pass ${it.absolutePath} " } ?: "") +
                "--dev crystaln0 --script-security 2 & echo \$! > ${pidFile.absolutePath}'",
        )
        val res = Privileges.runScript("ovpn-up", script)
        if (res != Privileges.Result.Ok) {
            file.delete()
            authFile?.delete()
            throw IllegalStateException(
                "openvpn не запущен: " + ((res as Privileges.Result.Fail).output.ifBlank { "отказано в доступе" }),
            )
        }
        openvpnFiles = listOfNotNull(file, authFile)
        openvpn = ChildProcess(
            "openvpn-watch",
            listOf("sh", "-c", "while kill -0 $(cat ${pidFile.absolutePath}) 2>/dev/null; do sleep 1; done"),
        ) { /* watcher is silent; openvpn output went to the elevated session */ }
        markConnected(p, "openvpn")
    }

internal fun TunnelManager.teardownAll() {
        polling = false
        if (com.crystaln.vpn.desktop.Platform.isWindows) WindowsHelper.stopStatsPoller()
        val mode = _ui.value.mode
        var stoppedSomething = false
        when (mode) {
            "wg" -> {
                if (com.crystaln.vpn.desktop.Platform.isWindows) {
                    if (teardownWgServiceWindows()) stoppedSomething = true
                } else {
                    wgConfPath?.let { path ->
                        val name = path.substringAfterLast('/').removeSuffix(".conf")
                        Privileges.runScript("wg-down", listOf("wg-quick down $name", "rm -f $path"))
                        stoppedSomething = true
                    }
                    wgConfPath = null
                    if (teardownAwgTunnel()) stoppedSomething = true
                }
            }
            "awg" -> {
                if (com.crystaln.vpn.desktop.Platform.isWindows) {
                    if (teardownWgServiceWindows()) stoppedSomething = true
                } else if (teardownAwgTunnel()) stoppedSomething = true
            }
            "openvpn" -> {
                if (com.crystaln.vpn.desktop.Platform.isWindows) {
                    openvpnPidFile?.let { pid ->
                        Privileges.runScript("ovpn-down-win", listOf(killPidCommand(pid)))
                    }
                    stoppedSomething = true
                } else {
                    val pidFile = File(AppDirs.dataDir(), "openvpn.pid")
                    Privileges.runScript("ovpn-down", listOf("kill $(cat ${pidFile.absolutePath})", "rm -f ${pidFile.absolutePath}"))
                    openvpn?.stop()
                    openvpn = null
                    stoppedSomething = true
                }
                openvpnFiles.forEach { it.delete() }
                openvpnFiles = emptyList()
                openvpnPidFile = null
            }
            "tun" -> {
                if (com.crystaln.vpn.desktop.Platform.isWindows) {
                    teardownTunWindows()
                } else {
                    tun2socks?.stop()
                    tun2socks = null
                    teardownTunQuietly()
                }
                stoppedSomething = true
            }
        }
        if (xray != null) stoppedSomething = true
        xray?.stop()
        xray = null
        HysteriaRunner.stop()
        HysteriaRunner.stop()
        SingBoxRunner.stop()
        if (stoppedSomething) logLine("Туннель остановлен")
    }

private fun TunnelManager.teardownAwgTunnel(): Boolean {
        val iface = awgIface ?: return false
        Privileges.runScript(
            "awg-down",
            awgDownCommands(
                iface,
                File(AppDirs.dataDir(), "amneziawg.pid"),
                "/var/run/amneziawg/$iface.conf",
                "/var/run/amneziawg/$iface.sock",
            ),
        )
        awgIface = null
        return true
    }

private fun TunnelManager.teardownTunQuietly() {
        val dev = "crystaln0"
        val cmds = mutableListOf<String>()
        origGateway?.let { gw -> cmds += "ip route replace default via $gw" }
        cmds += "ip link del $dev"
        Privileges.runScript("tun-down", cmds)
        origGateway = null
    }

    /** Windows TUN teardown: убить tun2socks, снять маршруты (адаптер умирает с ним). */
private fun TunnelManager.teardownTunWindows() {
        val cmds = mutableListOf(
            killPidCommand(tun2socksPidFile ?: File(AppDirs.dataDir(), "tun2socks.pid")),
            // Снять наши маршруты; несуществующие (адаптер уже умер) — не ошибка.
            "route delete 0.0.0.0 mask 0.0.0.0 198.18.0.1 2>\$null; \$LASTEXITCODE = 0",
        )
        tunServerIp?.let { ip ->
            cmds += "route delete $ip mask 255.255.255.255 2>\$null; \$LASTEXITCODE = 0"
        }
        Privileges.runScript("tun-down-win", cmds)
        tun2socksPidFile = null
        tunServerIp = null
        origGateway = null
        origIfIndex = null
    }

    /** Windows: снять amneziawg tunnel service и удалить конфиг. */
private fun TunnelManager.teardownWgServiceWindows(): Boolean {
        val iface = wgService ?: return false
        val exe = File(AppDirs.binDir(), "${Binaries.AWG_SERVICE}.exe").absolutePath
        Privileges.runScript(
            "wg-service-down",
            listOf("& '$exe' /uninstalltunnelservice $iface | Out-Null; \$LASTEXITCODE = 0"),
        )
        File(File(AppDirs.dataDir(), "tunnels"), "$iface.conf").delete()
        wgService = null
        return true
    }

/** openvpn's auth-user-pass file has one unescaped username/password per line. */
internal fun openVpnCredentialText(user: String?, pass: String?): String? {
    val u = user?.trim().orEmpty()
    val p = pass?.trim().orEmpty()
    return if (u.isEmpty() && p.isEmpty()) null else "$u\n$p\n"
}
internal fun TunnelManager.connectSingBox(p: Profile) {
    val dataDir = AppDirs.configDir()
    val socksPort = SettingsStore.socksPort(dataDir)
    val httpPort = SettingsStore.httpPort(dataDir)
    val opts = XrayConfigBuilder.Options(
        dnsViaTunnel = SettingsStore.dnsViaTunnel(dataDir),
        dnsServer = SettingsStore.dnsServer(dataDir),
        blockAds = SettingsStore.blockAds(dataDir),
        splitTunnelMode = SettingsStore.splitTunnelMode(dataDir),
        splitTunnelRules = SettingsStore.splitTunnelRules(dataDir),
    )
    val bin = AppDirs.binDir()
    val st = Binaries.status()
    if (!st.xrayReady) throw IllegalStateException("xray not found")
    if (!st.singboxReady) throw IllegalStateException("sing-box not found")
    if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN && !st.tun2socksReady) {
        throw IllegalStateException("tun2socks not found")
    }
    if (SettingsStore.tunnelMode(dataDir) == SettingsStore.TunnelMode.TUN && !st.wintunReady) {
        throw IllegalStateException("wintun not found")
    }
    SingBoxRunner.start(p)
    val config = XrayConfigBuilder.buildProxy(p, opts, socksPort, httpPort)
    val configFile = File(AppDirs.dataDir(), "xray-config.json")
    SecureFiles.writeText(configFile, config)
    xray = ChildProcess(
        "xray",
        listOf(ChildProcess.bin(bin, Binaries.XRAY), "run", "-c", configFile.absolutePath),
    ) { logLine("[xray] $it") }
    Thread.sleep(500)
    if (xray?.alive != true) {
        SingBoxRunner.stop()
        throw IllegalStateException("xray exited immediately")
    }
    var socksUp = false
    repeat(30) {
        socksUp = try { Socket().use { s -> s.connect(InetSocketAddress("127.0.0.1", socksPort), 300); true } } catch (_: Throwable) { Thread.sleep(200); false }
        if (socksUp) return@repeat
    }
    if (!socksUp) { SingBoxRunner.stop(); xray?.stop(); throw IllegalStateException("xray proxy port not opened") }
    val mode = SettingsStore.tunnelMode(dataDir)
    if (mode == SettingsStore.TunnelMode.PROXY) { markConnected(p, "proxy"); return }
    if (com.crystaln.vpn.desktop.Platform.isWindows) {
        SingBoxRunner.stop(); xray?.stop(); throw IllegalStateException("TUN mode for sing-box on Windows not supported yet - use proxy mode")
    }
    origGateway = defaultGateway()
    val dev = "crystaln0"
    val setup = listOf("ip tuntap add mode tun dev $dev", "ip addr add 198.18.0.1/15 dev $dev", "ip link set $dev up", "ip route replace default dev $dev")
    val res = Privileges.runScript("tun-setup", setup)
    if (res != Privileges.Result.Ok) {
        teardownTunQuietly(); xray?.stop(); SingBoxRunner.stop()
        throw IllegalStateException("TUN setup failed")
    }
    tun2socks = ChildProcess("tun2socks", listOf(ChildProcess.bin(bin, Binaries.TUN2SOCKS), "--device", dev, "--proxy", "socks5://127.0.0.1:$socksPort", "--loglevel", "warn")) { logLine("[tun2socks] $it") }
    Thread.sleep(700)
    if (tun2socks?.alive != true) {
        teardownTunQuietly(); xray?.stop(); SingBoxRunner.stop()
        throw IllegalStateException("tun2socks exited")
    }
    markConnected(p, "tun")
}
