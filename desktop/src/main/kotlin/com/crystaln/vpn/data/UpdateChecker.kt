package com.crystaln.vpn.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Checks the public GitHub repo for a newer release. Blocking network call —
 * must run off the UI thread. The button in the UI opens the releases page
 * (no in-app binary download: the user picks the right artifact themselves).
 */
object UpdateChecker {

    const val REPO = "lightcodegit/crystaln"
    const val RELEASES_URL = "https://github.com/$REPO/releases/latest"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val TIMEOUT_MS = 10_000

    data class Release(
        /** Version without the leading "v", e.g. "1.2.0". */
        val version: String,
        /** HTML page of the release (GitHub releases overview on fallback). */
        val url: String,
    )

    /**
     * Returns the newest release when it is newer than [currentVersion],
     * otherwise null. Never throws: on any error returns null (silently —
     * the update hint is best-effort, no toast for offline machines).
     */
    fun check(currentVersion: String): Release? {
        return try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub API requires a User-Agent.
                setRequestProperty("User-Agent", "CrystalN")
            }
            try {
                val body = conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray().toString(Charsets.UTF_8)
                }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").removePrefix("v").trim()
                if (tag.isEmpty()) return null
                if (!isNewer(tag, currentVersion)) return null
                Release(
                    version = tag,
                    url = json.optString("html_url").takeIf { it.startsWith("https://") }
                        ?: RELEASES_URL,
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Numeric component-wise comparison: 1.2.10 > 1.2.9; ignores suffixes. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.substringBefore('-').split('.').map { it.toLongOrNull() ?: 0L }
        val c = current.substringBefore('-').split('.').map { it.toLongOrNull() ?: 0L }
        val size = maxOf(l.size, c.size)
        for (i in 0 until size) {
            val a = l.getOrElse(i) { 0L }
            val b = c.getOrElse(i) { 0L }
            if (a != b) return a > b
        }
        return false
    }
}
