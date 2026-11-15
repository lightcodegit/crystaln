package com.crystaln.vpn.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.crystaln.vpn.R
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: one tap toggles the selected profile's tunnel.
 *
 * Connect runs straight from the tile when the VPN permission is already
 * granted (it persists); otherwise MainActivity is opened so the system
 * permission dialog can be shown — a tile tap cannot host that dialog.
 */
class CrystalTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        update()
        // Keep the tile in sync while it is visible (e.g. the user toggled
        // from the notification or the app itself).
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                VpnManager.ui.collect { update() }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val appContext = applicationContext
            when (VpnManager.current().state) {
                VpnManager.State.OFF -> tryConnectFromTile(appContext)
                else -> VpnManager.stop(appContext)
            }
        }
        update()
    }

    private fun tryConnectFromTile(context: Context) {
        val profile = ProfileStore.selected(context)
            ?: ProfileStore.load(context).firstOrNull()
        if (profile == null) {
            openMainActivity()
            return
        }
        // null = permission already granted, connect right from the tile.
        if (VpnService.prepare(context) != null) {
            openMainActivity()
            return
        }
        VpnManager.connect(context, profile)
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this,
                    3001,
                    intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun update() {
        val tile = qsTile ?: return
        val st = VpnManager.current()
        when (st.state) {
            VpnManager.State.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = getString(R.string.tile_on, st.profile?.name ?: "")
            }

            VpnManager.State.STARTING, VpnManager.State.STOPPING -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_transition)
            }

            VpnManager.State.OFF -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_off)
            }
        }
        tile.updateTile()
    }
}
