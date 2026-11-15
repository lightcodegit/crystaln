package com.crystaln.vpn.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * One shared periodic job keeps every enabled subscription fresh in the
 * background. WorkManager survives process death and reboots; a manual refresh
 * can always force an immediate update.
 */
object SubscriptionSyncScheduler {
    private const val UNIQUE_WORK_NAME = "crystaln_subscription_sync"
    private const val DEFAULT_INTERVAL_HOURS = 6L

    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val anyEnabled = SubscriptionsStore.load(context).any { it.enabled && it.autoRefresh }
        if (!anyEnabled) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<SubscriptionSyncWorker>(
            DEFAULT_INTERVAL_HOURS,
            TimeUnit.HOURS,
        ).build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

class SubscriptionSyncWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // Individual failures are stored in Subscription.lastError; the next
        // periodic run retries automatically. Never fail the whole chain here.
        SubscriptionRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}
