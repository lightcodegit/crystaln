package com.crystaln.vpn.data

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads one remote subscription body and merges it into the profile store. */
object SubscriptionRefresher {

    /** Keep in sync with the UI's import cap: subscriptions should be small. */
    private const val MAX_BODY_BYTES = 1L * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000

    data class RefreshResult(
        val ok: Boolean,
        val added: Int = 0,
        val updated: Int = 0,
        val removed: Int = 0,
        val error: String? = null,
    )

    fun refresh(context: Context, subscription: Subscription): RefreshResult {
        return try {
            val text = download(subscription.url)
            val parsed = ProfileImporter.parseText(text)
            if (parsed.isEmpty()) {
                val msg = "empty subscription"
                SubscriptionsStore.setStatus(context, subscription.id, false, msg)
                RefreshResult(ok = false, error = msg)
            } else {
                val merge = ProfileStore.mergeProfiles(
                    context,
                    parsed,
                    subscription.id,
                    selectFirst = false,
                )
                SubscriptionsStore.setStatus(context, subscription.id, true)
                RefreshResult(
                    ok = true,
                    added = merge.added,
                    updated = merge.updated,
                    removed = merge.removed,
                )
            }
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            SubscriptionsStore.setStatus(context, subscription.id, false, msg)
            RefreshResult(ok = false, error = msg)
        }
    }

    /** Refreshes every enabled subscription; used by manual refresh and WorkManager. */
    fun refreshAll(context: Context): List<Pair<Subscription, RefreshResult>> =
        SubscriptionsStore.load(context)
            .filter { it.enabled }
            .map { it to refresh(context, it) }

    private fun download(url: String): String {
        // Reject non-HTTP schemes before the cast (file:/jar: would otherwise
        // be accepted by URL.openConnection and crash or read local files).
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            throw java.io.IOException("Unsupported URL scheme")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
        }
        try {
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_BODY_BYTES) throw IOException("subscription too large")
                    out.write(buf, 0, n)
                }
                return out.toString("UTF-8")
            }
        } finally {
            conn.disconnect()
        }
    }
}
