package com.crystaln.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore
import com.crystaln.vpn.hysteria.HysteriaCore
import com.crystaln.vpn.singbox.SingBoxCore
import com.crystaln.vpn.ui.MainActivity
import com.crystaln.vpn.xray.XrayConfigBuilder
import com.crystaln.vpn.xray.XrayCore
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VpnService for the Xray-family protocols.
 *
 * Runs the Xray core with a TUN inbound bound to the interface established
 * here. The app's own package is disallowed from the VPN so core traffic
 * bypasses the tunnel (no protect() per-socket dance needed).
 */
class CrystalVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                val id = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val profile = id?.let { ProfileStore.load(this).firstOrNull { p -> p.id == it } }
                    ?: ProfileStore.selected(this)
                if (profile == null || profile.isWireGuardFamily || profile.isOpenVpn) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                running = true
                startTunnel(profile)
            }
        }
        return START_STICKY
    }

    private fun startTunnel(profile: Profile) {
        notifyConnecting(profile.name)
        VpnManager.notifyState(VpnManager.State.STARTING, profile)
        scope.launch {
            try {
                XrayCore.initEnv(applicationContext)
                val fd = establishInterface(profile)
                if (fd == null) {
                    fail("establish() returned null")
                    return@launch
                }
                tun = fd
                // Hysteria2: the hysteria core (child process) must be up and
                // serving its local SOCKS before the Xray router starts — its
                // outbound points there.
                if (profile.isHysteria) {
                    try {
                        HysteriaCore.start(applicationContext, profile)
                    } catch (t: Throwable) {
                        fail(t.message ?: "hysteria core failed to start")
                        return@launch
                    }
                }
                // Sing-box family (TUIC/AnyTLS/Hysteria v1): same pattern.
                if (profile.isSingBoxFamily) {
                    try {
                        SingBoxCore.start(applicationContext, profile)
                    } catch (t: Throwable) {
                        fail(t.message ?: "sing-box core failed to start")
                        return@launch
                    }
                }
                // A started core says nothing about the server: it binds the
                // TUN happily even with the server off. Wait for the first
                // bytes received through the proxy outbound (system network
                // validation probes the new VPN within seconds) before
                // claiming CONNECTED.
                val configJson = XrayConfigBuilder.build(
                    profile,
                    XrayConfigBuilder.Options(
                        dnsViaTunnel = SettingsStore.dnsViaTunnel(applicationContext),
                        dnsServer = SettingsStore.dnsServer(applicationContext),
                        blockAds = SettingsStore.blockAds(applicationContext),
                        probePort = PROBE_SOCKS_PORT,
                    ),
                )
                XrayCore.start(configJson, fd.fd)
                VpnManager.notifyChecking(true)
                val timeoutMs = SettingsStore.checkTimeoutSec(applicationContext)
                    .coerceIn(3, 60) * 1000L
                if (!awaitConnectionProof(
                        applicationContext,
                        timeoutMs,
                    )
                ) {
                    fail(getString(R.string.server_unreachable))
                    return@launch
                }
                VpnManager.notifyChecking(false)
                VpnManager.notifyState(VpnManager.State.CONNECTED, profile)
                notifyConnected(profile.name)
            } catch (t: Throwable) {
                fail(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    private fun establishInterface(profile: Profile): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(profile.name)
            .setMtu(XrayConfigBuilder.TUN_MTU)
            .addAddress("26.26.26.1", 30)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
        // The core's own outbound traffic must not loop back into the TUN.
        builder.addDisallowedApplication(packageName)
        // Split tunneling: exclude/include selected apps.
        val splitMode = SettingsStore.splitMode(applicationContext)
        val splitApps = SettingsStore.splitApps(applicationContext)
        if (splitMode == 1) {
            // Exclude selected apps from the VPN.
            splitApps.forEach { pkg ->
                try { builder.addDisallowedApplication(pkg) } catch (_: Throwable) { }
            }
        } else if (splitMode == 2) {
            // Only selected apps use the VPN: allow them, disallow everything else.
            // Android VpnService has no "disallow all except", so we mark the
            // whole installed set as disallowed first, then re-allow selected.
            // (Best-effort: apps installed after connect won't be covered.)
            try {
                applicationContext.packageManager.getInstalledApplications(0).forEach { ai ->
                    if (ai.packageName != packageName) {
                        try { builder.addDisallowedApplication(ai.packageName) } catch (_: Throwable) { }
                    }
                }
            } catch (_: Throwable) { }
            splitApps.forEach { pkg ->
                try { builder.addAllowedApplication(pkg) } catch (_: Throwable) { }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder.establish()
    }

    /**
     * Waits until the connection is proven live, using the user's configured
     * check URL and timeout. Two independent proofs, either is enough:
     * 1. An active HTTP probe through the tunnel: the app connects to the
     *    core's local SOCKS inbound (the app itself is excluded from the
     *    TUN, so a plain request would bypass the VPN) and requests the URL.
     * 2. The passive byte-wait fallback: the core's outbound reports
     *    received bytes (system validation traffic flowing through).
     */
    private suspend fun awaitConnectionProof(
        context: Context,
        timeoutMs: Long,
    ): Boolean {
        val url = SettingsStore.checkUrl(context)
        val deadline = System.currentTimeMillis() + timeoutMs
        val probe = { try {
            val conn = URL(url).openConnection(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", PROBE_SOCKS_PORT)),
            ) as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            val ok = conn.responseCode in 200..399
            conn.disconnect()
            ok
        } catch (_: Throwable) {
            false
        } }
        while (System.currentTimeMillis() < deadline) {
            if (!running) return false
            if (probe()) return true
            val traffic = XrayCore.traffic()
            if (traffic != null && traffic.first > 0) return true
            delay(600)
        }
        return false
    }

    private fun fail(message: String) {
        running = false
        shutdown()
        VpnManager.notifyError(message)
        stopSelf()
    }

    private fun shutdown() {
        running = false
        XrayCore.stop()
        HysteriaCore.stop()
        SingBoxCore.stop()
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
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

    private fun notifyConnecting(name: String) = notify(buildNotification(name, getString(R.string.notif_connecting)))

    private fun notifyConnected(name: String) {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CrystalVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Hands the foreground notification over to the status ticker: it
        // keeps posting on NOTIF_ID with live speed, replacing this row.
        VpnStatusNotification.startForeground(this, NOTIF_ID, CHANNEL_ID, name, stopIntent)
    }

    private fun buildNotification(name: String, text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CrystalVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSubText(name)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        startForeground(NOTIF_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "crystaln_vpn"
        const val NOTIF_ID = 1
        const val ACTION_START = "com.crystaln.vpn.START"
        const val ACTION_STOP = "com.crystaln.vpn.STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        /** Max wait for the first server response before declaring failure. */
        const val PROBE_TIMEOUT_MS = 15_000L

        /** Local SOCKS inbound port for the active connectivity probe. */
        private const val PROBE_SOCKS_PORT = 17533

        fun startIntent(context: Context, profile: Profile): Intent =
            Intent(context, CrystalVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profile.id)

        fun stopIntent(context: Context): Intent =
            Intent(context, CrystalVpnService::class.java).setAction(ACTION_STOP)
    }
}
