package com.crystaln.vpn.xray

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import java.util.UUID
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicReference

/**
 * Thin wrapper over the gobind bindings of Xray-core (AndroidLibXrayLite).
 *
 * One controller per running tunnel; [initEnv] must run before the first
 * start because the Go side reads the asset path and device key there.
 */
object XrayCore {

    @Volatile
    private var envInitialized = false

    private val controller = AtomicReference<CoreController?>(null)

    fun initEnv(context: Context) {
        if (envInitialized) return
        synchronized(this) {
            if (envInitialized) return
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, deviceKey(context))
            envInitialized = true
        }
    }

    /** Stable per-install key for XUDP: must be a Base64URL-encoded 32-byte
     *  value (Xray's common/xudp decodes it and requires exactly 32 bytes). */
    private fun deviceKey(context: Context): String {
        val prefs = context.getSharedPreferences("crystaln", Context.MODE_PRIVATE)
        val stored = prefs.getString("xudp_key", null)
        val valid = stored != null && try {
            java.util.Base64.getUrlDecoder().decode(stored).size == 32
        } catch (_: Throwable) { false }
        if (valid) return stored!!
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        prefs.edit().putString("xudp_key", encoded).apply()
        return encoded
    }

    fun version(): String = try {
        Libv2ray.checkVersionX()
    } catch (_: Throwable) {
        "unknown"
    }

    fun start(configJson: String, tunFd: Int) {
        stop()
        val handler = object : CoreCallbackHandler {
            override fun onEmitStatus(p0: Long, p1: String?): Long = 0
            override fun shutdown(): Long = 0
            override fun startup(): Long = 0
        }
        val c = Libv2ray.newCoreController(handler)
        controller.set(c)
        c.startLoop(configJson, tunFd)
    }

    fun stop() {
        controller.getAndSet(null)?.let { c ->
            try {
                c.stopLoop()
            } catch (_: Throwable) {
            }
        }
    }

    /** Returns (rx, tx) byte counters, or null when the core is not running. */
    fun traffic(): Pair<Long, Long>? {
        val c = controller.get() ?: return null
        return try {
            // Format: "tag,direction,value;tag,direction,value;..."
            var rx = 0L
            var tx = 0L
            c.queryAllOutboundTrafficStats().split(';').forEach { entry ->
                val parts = entry.split(',')
                if (parts.size >= 3 && parts[0] == "proxy") {
                    val v = parts[2].toLongOrNull() ?: return@forEach
                    when (parts[1]) {
                        "up" -> tx += v
                        "down" -> rx += v
                    }
                }
            }
            rx to tx
        } catch (_: Throwable) {
            null
        }
    }

    /** Outbound delay in ms through the given config, or -1 on failure. */
    fun measureDelay(configJson: String, url: String = "https://www.gstatic.com/generate_204"): Long = try {
        Libv2ray.measureOutboundDelay(configJson, url)
    } catch (_: Throwable) {
        -1L
    }
}
