package com.crystaln.vpn.singbox

import android.content.Context
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.xray.XrayConfigBuilder
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** Latency probe for sing-box family (TUIC/AnyTLS/Hysteria v1). */
object SingBoxPing {
    fun measure(context: Context, profile: Profile): Long {
        val start = System.currentTimeMillis()
        return try {
            SingBoxCore.start(context, profile)
            val conn = URL("https://www.gstatic.com/generate_204").openConnection(
                Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.SINGBOX_MIXED_PORT)),
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
            SingBoxCore.stop()
        }
    }
}
