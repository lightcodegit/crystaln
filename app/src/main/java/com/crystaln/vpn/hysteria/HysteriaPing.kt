package com.crystaln.vpn.hysteria

import android.content.Context
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.xray.XrayConfigBuilder
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Latency probe for Hysteria2: starts the core, measures a request through
 *  its local SOCKS5, then stops it. Returns ms or -1 on failure. */
object HysteriaPing {
    fun measure(context: Context, profile: Profile): Long {
        val start = System.currentTimeMillis()
        return try {
            HysteriaCore.start(context, profile)
            // wait for socks up (start() already waits, but be safe)
            val conn = URL("https://www.gstatic.com/generate_204").openConnection(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.HYSTERIA_SOCKS_PORT)),
            ) as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.responseCode
            conn.disconnect()
            System.currentTimeMillis() - start
        } catch (_: Throwable) {
            -1L
        } finally {
            HysteriaCore.stop()
        }
    }
}
