package com.crystaln.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.SettingsStore

/**
 * Connects the selected profile after boot when auto-connect is enabled in
 * the settings. The VPN permission persists across reboots, so connect()
 * can run straight from the receiver; a missing profile or disabled setting
 * is a no-op.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SettingsStore.autoConnect(context)) return
        val profile = ProfileStore.selected(context) ?: return
        VpnManager.connect(context.applicationContext, profile)
    }
}
