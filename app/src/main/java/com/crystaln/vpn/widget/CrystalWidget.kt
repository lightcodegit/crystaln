package com.crystaln.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import com.crystaln.vpn.R
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.service.VpnManager
import com.crystaln.vpn.ui.MainActivity

/**
 * Home-screen widget: current tunnel state plus a one-tap toggle, mirroring
 * the QS tile. Updates are pushed by [VpnManager] on every state change and
 * pulled when the launcher re-inflates the widget.
 */
class CrystalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val app = context.applicationContext
                when (VpnManager.current().state) {
                    VpnManager.State.OFF -> tryConnect(app)
                    else -> VpnManager.stop(app)
                }
            }
            ACTION_REFRESH -> Unit
        }
        updateAll(context)
    }

    private fun tryConnect(context: Context) {
        val profile = ProfileStore.selected(context)
            ?: ProfileStore.load(context).firstOrNull()
        if (profile == null || VpnService.prepare(context) != null) {
            // No profile or no VPN permission: the dialog needs an activity.
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        VpnManager.connect(context, profile)
    }

    companion object {
        const val ACTION_TOGGLE = "com.crystaln.vpn.widget.TOGGLE"
        const val ACTION_REFRESH = "com.crystaln.vpn.widget.REFRESH"

        /** Redraws every instance; cheap, called on state changes. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrystalWidget::class.java))
            ids.forEach { id -> updateOne(context, manager, id) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val st = VpnManager.current()
            val connected = st.state == VpnManager.State.CONNECTED
            val transitioning = st.state == VpnManager.State.STARTING ||
                st.state == VpnManager.State.STOPPING

            val rv = RemoteViews(context.packageName, R.layout.crystal_widget).apply {
                setTextViewText(
                    R.id.widget_state,
                    when {
                        connected -> context.getString(R.string.tile_on, st.profile?.name ?: "")
                        transitioning -> context.getString(R.string.tile_transition)
                        else -> context.getString(R.string.tile_off)
                    },
                )
                val subtitle = if (connected) {
                    val rx = st.rx / 1024
                    val tx = st.tx / 1024
                    "↓ ${rx} KB/s · ↑ ${tx} KB/s"
                } else context.getString(R.string.widget_tap_hint)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setInt(
                    R.id.widget_root,
                    "setBackgroundResource",
                    if (connected) R.drawable.widget_bg_on else R.drawable.widget_bg_off,
                )
                setInt(
                    R.id.widget_button,
                    "setBackgroundResource",
                    if (connected) R.drawable.widget_btn_on else R.drawable.widget_btn_off,
                )
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, CrystalWidget::class.java).setAction(ACTION_TOGGLE),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(id, rv)
        }

        /** Broadcasts a refresh to every instance (e.g. after process death). */
        fun requestRefresh(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setComponent(ComponentName(context, CrystalWidget::class.java)),
            )
        }
    }
}
