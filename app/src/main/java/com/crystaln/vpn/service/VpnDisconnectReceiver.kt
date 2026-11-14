package com.crystaln.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * "Отключить" action of the app-level status notification, used for tunnels
 * without an app-owned service (WireGuard/AWG): their VpnService lives in the
 * third-party GoBackend, so the button simply asks VpnManager to stop.
 */
class VpnDisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        VpnManager.stop(context)
    }
}
