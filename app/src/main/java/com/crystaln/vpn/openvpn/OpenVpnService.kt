package com.crystaln.vpn.openvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.ui.MainActivity
import java.io.BufferedWriter
import java.io.File
import java.io.FileDescriptor
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Live byte counters of the running OpenVPN tunnel, fed from the management
 * `>BYTECOUNT:in,out` messages (in = received, out = sent) and polled by
 * [VpnManager.pollTraffic].
 */
object OpenVpnStats {
    private val value = AtomicReference<Pair<Long, Long>?>(null)

    fun set(p: Pair<Long, Long>?) = value.set(p)

    fun clear() = value.set(null)

    fun traffic(): Pair<Long, Long>? = value.get()
}

/** One collected tun configuration between management queries and establish(). */
private class TunParams {
    var address: String? = null
    var prefix: Int = 24
    var address6: String? = null
    var prefix6: Int = 64
    var mtu: Int = 0
    val routes = mutableListOf<Pair<String, String>>() // network to netmask
    val routes6 = mutableListOf<Pair<String, Int>>() // network to prefix
    val dns = mutableListOf<String>()
    val dns6 = mutableListOf<String>()
    var domain: String? = null
}

/**
 * VpnService for OpenVPN profiles, running the official openvpn core built
 * for TARGET_ANDROID (libovpnexec.so, see tools/build_openvpn_core.sh).
 *
 * The core is executed as a child process (ProcessBuilder, exactly like
 * ics-openvpn) and talks to this service through the management interface —
 * a local unix socket the app listens on (`management <path> unix` +
 * `management-client` in the generated config). openvpn asks the app to
 * build the tun (`IFCONFIG`, `ROUTE`, `DNSSERVER`, `OPENTUN` NEED-OK
 * queries, see doc/android.txt in the openvpn tree): the service collects
 * the parameters, calls establish() and sends the tun fd back inside the
 * `needok 'OPENTUN' ok` message as an SCM_RIGHTS ancillary record —
 * LocalSocket.setFileDescriptorsForSend does that without any native code.
 * The app's own package is disallowed from the VPN, so the core's traffic
 * bypasses the tunnel without socket protect() calls.
 */
class OpenVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var running = false

    /** Flags set asynchronously by the management/log readers. */
    @Volatile
    private var connected = false

    @Volatile
    private var authFailed = false

    @Volatile
    private var optionsError: String? = null

    @Volatile
    private var lastLogLine: String? = null

    private var process: Process? = null
    private var server: LocalServerSocket? = null
    private var client: LocalSocket? = null
    private var tunPfd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                val id = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val profile = id?.let { ProfileStore.load(this).firstOrNull { p -> p.id == it } }
                    ?: ProfileStore.selected(this)
                if (profile == null || !profile.isOpenVpn) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                running = true
                startForegroundNotification(getString(R.string.notif_connecting))
                VpnManager.notifyState(VpnManager.State.STARTING, profile)
                scope.launch { runTunnel(profile) }
            }
        }
        return START_STICKY
    }

    private suspend fun runTunnel(profile: Profile) {
        try {
            val dir = File(filesDir, "openvpn/${profile.id}").apply { mkdirs() }
            val configFile = File(dir, "config.ovpn")
            val logFile = File(dir, "log.txt").apply { if (exists()) delete() }
            val socketFile = File(dir, "mgmt.sock").apply { if (exists()) delete() }

            // Credentials from the profile editor; written to a file because
            // openvpn reads user/password from a file, never from argv.
            val user = profile.fields["ovpnUser"]?.trim().orEmpty()
            val pass = profile.fields["ovpnPass"]?.trim().orEmpty()
            var authFile: File? = null
            if (user.isNotEmpty() || pass.isNotEmpty()) {
                authFile = File(dir, "auth.txt").apply {
                    writeText("$user\n$pass\n")
                    setReadable(false, false)
                    setWritable(false, false)
                    setReadable(true, true)
                    setWritable(true, true)
                }
            }

            val prepared = OpenVpnConfig.prepare(profile, socketFile.absolutePath, authFile)
            if (prepared == null) {
                fail(getString(R.string.ovpn_config_invalid))
                return
            }
            if (prepared.needsAuth && authFile == null) {
                fail(getString(R.string.ovpn_need_credentials))
                return
            }
            configFile.writeText(prepared.config)

            val bin = File(applicationInfo.nativeLibraryDir, "libovpnexec.so")
            if (!bin.exists()) {
                fail(getString(R.string.ovpn_core_missing))
                return
            }

            // App listens; openvpn connects (management-client). Same shape
            // as ics-openvpn: filesystem-namespace socket bound through a
            // LocalSocket, then a server socket over its fd.
            val serverLocal = LocalSocket()
            serverLocal.bind(LocalSocketAddress(socketFile.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
            val serverSocket = LocalServerSocket(serverLocal.fileDescriptor)
            server = serverSocket
            scope.launch { managementLoop(serverSocket, profile) }

            val pb = ProcessBuilder(listOf(bin.absolutePath, configFile.absolutePath))
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("LD_LIBRARY_PATH", applicationInfo.nativeLibraryDir)
                put("TMPDIR", dir.absolutePath)
            }
            val proc = try {
                pb.start()
            } catch (t: Throwable) {
                fail(t.message ?: t.javaClass.simpleName)
                return
            }
            process = proc
            thread(name = "ovpn-log-${profile.id.take(8)}") { readProcessLog(proc, logFile) }

            watchLoop(profile, proc)
        } catch (t: Throwable) {
            fail(t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * Management protocol loop. openvpn (re)connects to our listener; each
     * connection reads `>...` messages and answers them until the peer goes
     * away, then waits for the next connection.
     */
    private suspend fun managementLoop(serverSocket: LocalServerSocket, profile: Profile) {
        while (running) {
            val conn = try {
                serverSocket.accept()
            } catch (_: Throwable) {
                return // listener closed — service is stopping
            }
            client = conn
            val params = TunParams()
            try {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                var line = reader.readLine()
                while (line != null && running) {
                    handleManagementLine(line, params, profile)
                    line = reader.readLine()
                }
            } catch (_: Throwable) {
                // Connection dropped; openvpn reconnects on its own.
            } finally {
                if (client === conn) client = null
                try {
                    conn.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /** Dispatches one `>CMD:extra` message from openvpn. */
    private fun handleManagementLine(line0: String, params: TunParams, profile: Profile) {
        val line = line0.trim()
        if (!line.startsWith(">")) return
        val colon = line.indexOf(':')
        val cmd = if (colon >= 0) line.substring(1, colon) else line.substring(1)
        val extra = if (colon >= 0) line.substring(colon + 1) else ""
        when (cmd) {
            "INFO" -> {
                // Greeting banner; nothing to do.
            }

            "HOLD" -> {
                // management-hold: openvpn waits for our release, then we
                // turn on the messages we need.
                sendCommand("hold release")
                sendCommand("bytecount 1")
                sendCommand("state on")
            }

            "NEED-OK" -> processNeedOk(extra, params, profile)

            "PASSWORD" -> processPassword(extra, profile)

            "BYTECOUNT" -> {
                val parts = extra.split(',')
                if (parts.size >= 2) {
                    val rx = parts[0].trim().toLongOrNull()
                    val tx = parts[1].trim().toLongOrNull()
                    if (rx != null && tx != null) OpenVpnStats.set(rx to tx)
                }
            }

            "STATE" -> {
                // e.g. "1750000000,CONNECTED,SUCCESS" — the real "connected".
                if (extra.contains(",CONNECTED,SUCCESS")) {
                    connected = true
                }
            }

            else -> {
                // LOG, ECHO, ... are ignored; the child's stdout is our log.
            }
        }
    }

    private fun processNeedOk(extra: String, params: TunParams, profile: Profile) {
        val m = needOkRegex.find(extra) ?: return
        val type = m.groupValues[1]
        val payload = m.groupValues[2].trim()
        when (type) {
            "OPENTUN" -> {
                // Android only supports tun (not tap).
                if (!payload.equals("tun", ignoreCase = true)) {
                    sendCommand("needok 'OPENTUN' cancel")
                    return
                }
                handleOpenTun(params, profile)
            }

            "PERSIST_TUN_ACTION" ->
                // Always reopen: the new tun must exist before the old one
                // is closed (handled in handleOpenTun).
                sendCommand("needok 'PERSIST_TUN_ACTION' OPEN_BEFORE_CLOSE")

            "IFCONFIG" -> {
                // payload = "local remoteOrNetmask MTU topology"
                val p = payload.split(' ').map { it.trim() }
                if (p.size >= 3) {
                    params.address = p[0]
                    params.mtu = p[2].toIntOrNull() ?: params.mtu
                    params.prefix = if (p.getOrNull(3) == "subnet") {
                        netmaskToPrefix(p[1]) ?: 24
                    } else {
                        // Point-to-point: p[1] is the peer address; VpnService
                        // has no p2p concept, /30 is the usual convention.
                        30
                    }
                }
                sendCommand("needok 'IFCONFIG' ok")
            }

            "IFCONFIG6" -> {
                // payload = "ip/len mtu"
                val parts = payload.split(' ').firstOrNull()?.split('/')
                if (parts != null && parts.size >= 2) {
                    params.address6 = parts[0]
                    params.prefix6 = parts[1].toIntOrNull() ?: 64
                }
                sendCommand("needok 'IFCONFIG6' ok")
            }

            "ROUTE" -> {
                // payload = "network netmask gateway [dev iface]"
                val p = payload.split(' ').map { it.trim() }
                if (p.size >= 2) params.routes.add(p[0] to p[1])
                sendCommand("needok 'ROUTE' ok")
            }

            "ROUTE6" -> {
                // payload = "net/prefix dev"
                val p = payload.split(' ').firstOrNull()?.split('/')
                if (p != null && p.size >= 2) {
                    params.routes6.add(p[0] to (p[1].toIntOrNull() ?: 128))
                }
                sendCommand("needok 'ROUTE6' ok")
            }

            "DNSSERVER" -> {
                params.dns.add(payload)
                sendCommand("needok 'DNSSERVER' ok")
            }

            "DNS6SERVER" -> {
                params.dns6.add(payload)
                sendCommand("needok 'DNS6SERVER' ok")
            }

            "DNSDOMAIN" -> {
                params.domain = payload
                sendCommand("needok 'DNSDOMAIN' ok")
            }

            else -> sendCommand("needok '$type' ok")
        }
    }

    /** Answers credential queries coming through management-query-passwords. */
    private fun processPassword(extra: String, profile: Profile) {
        if (extra.startsWith("Verification Failed")) {
            if (extra.contains("'Auth'")) authFailed = true
            return
        }
        val m = passwordNeedRegex.find(extra) ?: return
        val type = m.groupValues[1]
        val what = m.groupValues[2]
        if (what == "username") {
            sendCommand("username '$type' ${profile.fields["ovpnUser"].orEmpty()}")
        } else {
            sendCommand("password '$type' ${profile.fields["ovpnPass"].orEmpty()}")
        }
    }

    private fun handleOpenTun(params: TunParams, profile: Profile) {
        val pfd = try {
            establishTun(params, profile)
        } catch (_: Throwable) {
            null
        }
        if (pfd == null) {
            sendCommand("needok 'OPENTUN' cancel")
            return
        }
        // The response line carries the tun fd as an SCM_RIGHTS ancillary
        // record — openvpn picks both up from the same read.
        val ok = sendCommandWithFd("needok 'OPENTUN' ok", pfd)
        if (!ok) {
            try {
                pfd.close()
            } catch (_: Throwable) {
            }
            return
        }
        // We answer PERSIST_TUN_ACTION with OPEN_BEFORE_CLOSE: the new tun
        // must exist before the old one is closed.
        val old = tunPfd
        tunPfd = pfd
        try {
            old?.close()
        } catch (_: Throwable) {
        }
    }

    /**
     * Sends [cmd] over the management socket with [pfd]'s fd attached via
     * SCM_RIGHTS — the hidden-but-stable `FileDescriptor.setInt$` trick,
     * exactly as ics-openvpn does (sendCommandWithFd).
     */
    private fun sendCommandWithFd(cmd: String, pfd: ParcelFileDescriptor): Boolean {
        val conn = client ?: return false
        return try {
            val fdtosend = FileDescriptor()
            val setInt = FileDescriptor::class.java.getDeclaredMethod("setInt\$", Int::class.javaPrimitiveType)
            setInt.isAccessible = true
            setInt.invoke(fdtosend, pfd.fd)
            synchronized(conn) {
                conn.setFileDescriptorsForSend(arrayOf(fdtosend))
            }
            val ok = sendCommand(cmd)
            synchronized(conn) {
                // The queue is one deep and would otherwise re-send the fd
                // on every future write.
                conn.setFileDescriptorsForSend(null)
            }
            ok
        } catch (_: Throwable) {
            false
        }
    }

    private fun sendCommand(cmd: String): Boolean {
        val conn = client ?: return false
        return try {
            synchronized(conn) {
                conn.outputStream.write((cmd.trim() + "\n").toByteArray(Charsets.UTF_8))
                conn.outputStream.flush()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** VpnService TUN built from the collected IFCONFIG/ROUTE/DNS queries. */
    private fun establishTun(params: TunParams, profile: Profile): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(if (params.mtu in 576..65535) params.mtu else 1500)
        val address = params.address
        if (address != null) {
            builder.addAddress(address, params.prefix)
        } else {
            // openvpn always sends IFCONFIG before OPENTUN; the fallback only
            // covers a config without any addressing at all.
            builder.addAddress("26.26.26.1", 30)
        }
        params.address6?.let { builder.addAddress(it, params.prefix6) }

        if (params.routes.isEmpty()) {
            builder.addRoute("0.0.0.0", 0)
        } else {
            params.routes.forEach { (network, netmask) ->
                val prefix = netmaskToPrefix(netmask) ?: 32
                try {
                    builder.addRoute(network, prefix)
                } catch (_: IllegalArgumentException) {
                }
            }
        }
        params.routes6.forEach { (network, prefix) ->
            try {
                builder.addRoute(network, prefix)
            } catch (_: IllegalArgumentException) {
            }
        }

        if (params.dns.isEmpty() && params.dns6.isEmpty()) {
            builder.addDnsServer("1.1.1.1")
            builder.addDnsServer("8.8.8.8")
        }
        params.dns.forEach { try { builder.addDnsServer(it) } catch (_: IllegalArgumentException) {} }
        params.dns6.forEach { try { builder.addDnsServer(it) } catch (_: IllegalArgumentException) {} }
        params.domain?.takeIf { it.isNotEmpty() }?.let { builder.addSearchDomain(it) }

        // Core traffic (the openvpn child belongs to this package) must not
        // loop back into the TUN.
        builder.addDisallowedApplication(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder.establish()
    }

    /**
     * Appends the child's stdout/stderr to log.txt and raises the error
     * flags the watch loop reacts to.
     */
    private fun readProcessLog(proc: Process, logFile: File) {
        try {
            val writer = BufferedWriter(FileWriter(logFile, true))
            proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                lastLogLine = line
                synchronized(writer) {
                    try {
                        writer.write(line)
                        writer.newLine()
                        writer.flush()
                    } catch (_: Throwable) {
                    }
                }
                if (line.contains("AUTH_FAILED")) authFailed = true
                if (line.startsWith("Options error") && optionsError == null) optionsError = line
                if (line.contains("Initialization Sequence Completed")) connected = true
            }
            try {
                writer.close()
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
            // Stream closed during shutdown.
        }
    }

    /**
     * Watches the openvpn child: reacts to the error flags raised by the
     * management/log readers, publishes the connected state, enforces the
     * connect timeout and detects process death.
     */
    private suspend fun watchLoop(profile: Profile, proc: Process) {
        val started = System.currentTimeMillis()
        var notifiedConnected = false
        while (running) {
            delay(500)
            if (!notifiedConnected && connected) {
                notifiedConnected = true
                VpnManager.notifyState(VpnManager.State.CONNECTED, profile)
                startForegroundNotification(getString(R.string.notif_connected, profile.name))
            }
            if (authFailed) {
                fail(getString(R.string.ovpn_auth_failed))
                return
            }
            optionsError?.let {
                fail(it)
                return
            }
            if (!connected && System.currentTimeMillis() - started > CONNECT_TIMEOUT_MS) {
                fail(getString(R.string.ovpn_timeout))
                return
            }
            if (!proc.isAlive) {
                // Give the log reader a moment to flush the final lines.
                delay(500)
                fail(lastLogLine ?: getString(R.string.ovpn_exited))
                return
            }
        }
    }

    /** Dotted netmask ("255.255.255.0") or "net/prefix" to prefix length, or null. */
    private fun netmaskToPrefix(mask: String): Int? {
        if (mask.contains('/')) {
            val n = mask.substringAfter('/').toIntOrNull()
            return if (n != null && n in 0..32) n else null
        }
        val octets = mask.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        var prefix = 0
        var expectingOnes = true
        for (o in octets) {
            if (o == 255 && expectingOnes) {
                prefix += 8
                continue
            }
            if (o == 0) {
                expectingOnes = false
                continue
            }
            if (!expectingOnes) return null
            // Partial octet: must be contiguous high bits (e.g. 240 = /28).
            var v = o
            var ones = 0
            while (v and 0x80 != 0) {
                ones++
                v = (v shl 1) and 0xFF
            }
            if (v != 0) return null
            prefix += ones
            expectingOnes = false
        }
        return prefix
    }

    private fun fail(message: String) {
        shutdown()
        VpnManager.notifyError(message)
        stopSelf()
    }

    private fun shutdown() {
        running = false
        // Politely ask openvpn to terminate via the management channel
        // (works while the socket is still open), then tear everything down.
        sendCommand("signal SIGTERM")
        val conn = client
        client = null
        try {
            conn?.close()
        } catch (_: Throwable) {
        }
        val srv = server
        server = null
        try {
            srv?.close()
        } catch (_: Throwable) {
        }
        val p = process
        process = null
        if (p != null) {
            cleanupScope.launch {
                try {
                    p.destroy()
                    delay(3000)
                    if (p.isAlive) p.destroyForcibly()
                } catch (_: Throwable) {
                }
            }
        }
        try {
            tunPfd?.close()
        } catch (_: Throwable) {
        }
        tunPfd = null
        OpenVpnStats.clear()
    }

    override fun onRevoke() {
        shutdown()
        VpnManager.notifyState(VpnManager.State.OFF, null)
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        scope.cancel()
        VpnManager.notifyState(VpnManager.State.OFF, null)
        super.onDestroy()
    }

    private fun startForegroundNotification(text: String) {
        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, OpenVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            4,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        startForeground(NOTIF_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "crystaln_vpn_openvpn"
        const val NOTIF_ID = 2
        const val ACTION_START = "com.crystaln.vpn.OVPN_START"
        const val ACTION_STOP = "com.crystaln.vpn.OVPN_STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        /** Give up if the handshake never completes. */
        const val CONNECT_TIMEOUT_MS = 45_000L

        /** ">NEED-OK:Need 'TYPE' confirmation MSG:payload" (also matches without confirmation). */
        private val needOkRegex = Regex("Need '([^']+)'(?: confirmation)?(?: MSG:(.*))?$")

        /** ">PASSWORD:Need 'Auth' username" / "... password". */
        private val passwordNeedRegex = Regex("Need '([^']+)' (username|password)")

        fun startIntent(context: Context, profile: Profile): Intent =
            Intent(context, OpenVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profile.id)

        fun stopIntent(context: Context): Intent =
            Intent(context, OpenVpnService::class.java).setAction(ACTION_STOP)
    }
}
