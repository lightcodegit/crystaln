package com.crystaln.vpn.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.service.VpnManager

/**
 * RemoteViewsService backing the 4x2 list widget: one row per profile,
 * favorites first. The factory re-reads profiles on every rebind, so rows
 * are always built from the persisted store.
 */
class CrystalListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(applicationContext)
}

private class Factory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var profiles: List<Profile> = emptyList()

    override fun onCreate() {
        loadData()
    }

    override fun onDataSetChanged() {
        loadData()
    }

    private fun loadData() {
        profiles = ProfileStore.load(context).sortedByDescending { it.favorite }
    }

    override fun onDestroy() {
        profiles = emptyList()
    }

    override fun getCount(): Int = profiles.size

    override fun getViewTypeCount(): Int = 1

    override fun getLoadingView(): RemoteViews? = null

    override fun hasStableIds(): Boolean = true

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewAt(position: Int): RemoteViews {
        val profile = profiles[position]
        val st = VpnManager.current()
        val live = st.profile?.id == profile.id && st.state != VpnManager.State.OFF
        val connecting = live && st.state != VpnManager.State.CONNECTED

        return RemoteViews(context.packageName, R.layout.widget_list_item).apply {
            // Dot: green = connected, amber = in transition, gray = idle.
            setInt(
                R.id.widget_item_dot,
                "setColorFilter",
                when {
                    connecting -> 0xFFE8A238.toInt()
                    live -> 0xFF2BB673.toInt()
                    else -> 0x669AA1BC.toInt()
                },
            )
            setTextViewText(R.id.widget_item_name, profile.name)
            setTextViewText(
                R.id.widget_item_state,
                when {
                    connecting -> context.getString(R.string.tile_transition)
                    live -> context.getString(R.string.widget_item_connected)
                    else -> ""
                },
            )
            setOnClickFillInIntent(
                R.id.widget_item_root,
                Intent().putExtra(CrystalListWidget.EXTRA_PROFILE_ID, profile.id),
            )
        }
    }
}
