package com.crystaln.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.crystaln.vpn.R
import com.crystaln.vpn.ui.MainActivity
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ongoing "VPN подключён" notification with live download/upload speed.
 *
 * The rate is sampled once per second from [VpnManager.pollTraffic] (the
 * cumulative byte counters of whichever engine is active) and shown as the
 * delta between two samples.
 *
 * Engines with an app-owned [Service] (Xray family, OpenVPN) call
 * [startForeground]: the ticker keeps posting on the service's own
 * notification id, so the "connecting" notification is replaced in place
 * and no duplicate entries appear. WireGuard-family profiles run inside the
 * third-party GoBackend service and use [startApp] to post an app-level
 * notification instead. [stop] cancels the ticker and removes the row; it
 * is wired into every OFF transition of [VpnManager].
 */
object VpnStatusNotification {

    /** Channel/id used when there is no app-owned foreground service (WireGuard). */
    const val APP_CHANNEL_ID = "crystaln_vpn_status"
    const val APP_NOTIFICATION_ID = 1001

    private const val SAMPLE_INTERVAL_MS = 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var job: Job? = null
    private val lock = Any()

    /** Notification id of the currently shown status row (cleared by [stop]). */
    private var activeId = -1

    /** Application context captured when the row was posted; used by [stop]. */
    private var appContext: Context? = null
    private var lastRx = 0L
    private var lastTx = 0L

    private class Target(
        val appContext: Context,
        val id: Int,
        val channelId: String,
        val profileName: String,
        val stopIntent: PendingIntent?,
        val openIntent: PendingIntent,
        /** Posts the very first notification (foreground services use startForeground). */
        val postInitial: (Notification) -> Unit,
    )

    /**
     * Replaces [service]'s foreground notification with the connected+speed
     * one and starts the ticker. Must be called when the tunnel is up.
     */
    fun startForeground(
        service: Service,
        notificationId: Int,
        channelId: String,
        profileName: String,
        stopIntent: PendingIntent,
    ) {
        begin(
            Target(
                appContext = service.applicationContext,
                id = notificationId,
                channelId = channelId,
                profileName = profileName,
                stopIntent = stopIntent,
                openIntent = openIntent(service, 0),
            ) { service.startForeground(notificationId, it) },
        )
    }

    /** App-level notification for engines without an app-owned service (WireGuard). */
    fun startApp(context: Context, profileName: String, stopIntent: PendingIntent? = null) {
        begin(
            Target(
                appContext = context.applicationContext,
                id = APP_NOTIFICATION_ID,
                channelId = APP_CHANNEL_ID,
                profileName = profileName,
                stopIntent = stopIntent,
                openIntent = openIntent(context, 0),
            ) { NotificationManagerCompat.from(context).notify(APP_NOTIFICATION_ID, it) },
        )
    }

    /** Cancels the ticker and removes the currently shown status row, if any. */
    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            val id = activeId
            val ctx = appContext
            if (id >= 0 && ctx != null) {
                NotificationManagerCompat.from(ctx).cancel(id)
            }
            activeId = -1
            appContext = null
        }
    }

    private fun openIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun begin(t: Target) {
        synchronized(lock) {
            job?.cancel()
            activeId = t.id
            appContext = t.appContext
            lastRx = 0L
            lastTx = 0L
        }
        ensureChannel(t.appContext, t.channelId)
        t.postInitial(build(t, 0L, 0L))
        val newJob = scope.launch {
            var primed = false
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                val rx = VpnManager.current().rx
                val tx = VpnManager.current().tx
                val rxRate: Long
                val txRate: Long
                synchronized(lock) {
                    if (primed) {
                        rxRate = (rx - lastRx).coerceAtLeast(0)
                        txRate = (tx - lastTx).coerceAtLeast(0)
                    } else {
                        rxRate = 0
                        txRate = 0
                        primed = true
                    }
                    lastRx = rx
                    lastTx = tx
                }
                NotificationManagerCompat.from(t.appContext).notify(t.id, build(t, rxRate, txRate))
            }
        }
        synchronized(lock) { job = newJob }
    }

    private fun build(t: Target, rxRate: Long, txRate: Long): Notification {
        val b = NotificationCompat.Builder(t.appContext, t.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(t.appContext.getString(R.string.notif_vpn_connected))
            .setContentText(
                t.appContext.getString(
                    R.string.notif_speed_format,
                    formatSpeed(t.appContext, rxRate),
                    formatSpeed(t.appContext, txRate),
                ),
            )
            .setSubText(t.profileName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(t.openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        t.stopIntent?.let { b.addAction(0, t.appContext.getString(R.string.action_stop), it) }
        return b.build()
    }

    private fun ensureChannel(context: Context, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun formatSpeed(context: Context, bytesPerSec: Long): String = when {
        bytesPerSec >= 1L shl 30 -> context.getString(R.string.speed_gbps_fmt, bytesPerSec / 1073741824.0)
        bytesPerSec >= 1L shl 20 -> context.getString(R.string.speed_mbps_fmt, bytesPerSec / 1048576.0)
        bytesPerSec >= 1L shl 10 -> context.getString(R.string.speed_kbps_fmt, bytesPerSec / 1024.0)
        else -> context.getString(R.string.speed_bps_fmt, bytesPerSec)
    }
}
