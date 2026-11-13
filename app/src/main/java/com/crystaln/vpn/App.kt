package com.crystaln.vpn

import android.app.Application
import com.crystaln.vpn.xray.XrayCore

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Preload the Xray environment once per process.
        try {
            XrayCore.initEnv(this)
        } catch (_: Throwable) {
            // The AAR may be missing in a WireGuard-only build; surface later on connect.
        }
    }
}
