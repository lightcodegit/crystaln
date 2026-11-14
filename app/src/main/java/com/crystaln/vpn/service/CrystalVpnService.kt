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
import com.crystaln.vpn.ui.MainActivity
import com.crystaln.vpn.xray.XrayConfigBuilder
import com.crystaln.vpn.xray.XrayCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
                XrayCore.start(XrayConfigBuilder.build(profile), fd.fd)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder.establish()
    }

    private fun fail(message: String) {
        shutdown()
        VpnManager.notifyError(message)
        stopSelf()
    }

    private fun shutdown() {
        XrayCore.stop()
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
            .setSmallIcon(R.drawable.ic_launcher)
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

        fun startIntent(context: Context, profile: Profile): Intent =
            Intent(context, CrystalVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profile.id)

        fun stopIntent(context: Context): Intent =
            Intent(context, CrystalVpnService::class.java).setAction(ACTION_STOP)
    }
}
