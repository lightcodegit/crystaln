package com.crystaln.vpn

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.crystaln.vpn.data.SubscriptionSyncScheduler
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
        initWorkManager()
        // Reconcile the periodic subscription job after process death/reboot.
        try {
            SubscriptionSyncScheduler.reschedule(this)
        } catch (_: Throwable) {
            // Background refresh is best-effort; never block app startup.
        }
    }

    /**
     * Usually the androidx.startup provider initializes WorkManager before
     * Application.onCreate, but Robolectric tests skip provider
     * initialization — this makes it deterministic in both environments.
     */
    private fun initWorkManager() {
        try {
            WorkManager.getInstance(this)
        } catch (_: IllegalStateException) {
            try {
                WorkManager.initialize(this, Configuration.Builder().build())
            } catch (_: Throwable) {
                // Background refresh is best-effort.
            }
        }
    }
}
