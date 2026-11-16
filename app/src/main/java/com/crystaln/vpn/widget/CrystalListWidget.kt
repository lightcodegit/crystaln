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
 * 4x2 home-screen widget: a scrollable list of server profiles. Rows are
 * built by [CrystalListWidgetService]; tapping a row connects that profile
 * (tapping the live one stops the tunnel). State changes are pushed by
 * [VpnManager] via [updateAll].
 */
class CrystalListWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_ITEM_CLICK -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
                val app = context.applicationContext
                val profile = ProfileStore.load(app).firstOrNull { it.id == profileId }
                    ?: return
                val st = VpnManager.current()
                when {
                    st.profile?.id == profileId && st.state != VpnManager.State.OFF ->
                        // Tapping the live profile toggles it off.
                        VpnManager.stop(app)

                    VpnService.prepare(app) != null ->
                        // No VPN permission yet: the dialog needs an activity.
                        app.startActivity(
                            Intent(app, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )

                    else -> {
                        ProfileStore.select(app, profileId)
                        VpnManager.connect(app, profile)
                    }
                }
            }
        }
        updateAll(context)
    }

    companion object {
        const val ACTION_ITEM_CLICK = "com.crystaln.vpn.widget.LIST_ITEM_CLICK"
        const val EXTRA_PROFILE_ID = "profile_id"

        /** Redraws every instance; cheap, called on state changes. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrystalListWidget::class.java))
            ids.forEach { id -> updateOne(context, manager, id) }
        }

        /** Profile list changed: rebind the collection so rows re-read. */
        fun notifyDataChanged(context: Context) {
            updateAll(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrystalListWidget::class.java))
            ids.forEach { manager.notifyAppWidgetViewDataChanged(it, R.id.widget_list_list) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val st = VpnManager.current()
            val connected = st.state == VpnManager.State.CONNECTED

            val rv = RemoteViews(context.packageName, R.layout.widget_list).apply {
                setInt(
                    R.id.widget_list_root,
                    "setBackgroundResource",
                    if (connected) R.drawable.widget_bg_on else R.drawable.widget_bg_off,
                )
                // Rows come from the service factory.
                setRemoteAdapter(
                    R.id.widget_list_list,
                    Intent(context, CrystalListWidgetService::class.java),
                )
                // One pending-intent template: each row fills in its profile id.
                setPendingIntentTemplate(
                    R.id.widget_list_list,
                    PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, CrystalListWidget::class.java).setAction(ACTION_ITEM_CLICK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(id, rv)
        }
    }
}
