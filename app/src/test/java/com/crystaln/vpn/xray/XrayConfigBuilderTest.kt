package com.crystaln.vpn.xray

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The generated core config is the safety boundary between UI data and the
 * native core: a mistake here can turn a profile into a broken or unsafe
 * tunnel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XrayConfigBuilderTest {

    @Test
    fun buildsVlessTcpTlsOutbound() {
        val p = Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
            address = "vless.example.com"
            port = 443
            fields["uuid"] = "uuid"
            fields["network"] = "tcp"
            fields["tls"] = "true"
            fields["sni"] = "sni.example.com"
        }
        val root = JSONObject(XrayConfigBuilder.build(p))
        assertEquals("warning", root.getJSONObject("log").getString("loglevel"))
        val outbounds = root.getJSONArray("outbounds")
        assertEquals(3, outbounds.length())
        val proxy = outbounds.getJSONObject(0)
        assertEquals("proxy", proxy.getString("tag"))
        assertEquals("vless", proxy.getString("protocol"))
        val server = proxy.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0)
        assertEquals("vless.example.com", server.getString("address"))
        assertEquals(443, server.getInt("port"))
        val user = server.getJSONArray("users").getJSONObject(0)
        assertEquals("uuid", user.getString("id"))
        assertEquals("none", user.getString("encryption"))
        val tls = proxy.getJSONObject("streamSettings").getJSONObject("tlsSettings")
        assertEquals("sni.example.com", tls.getString("serverName"))
        // Local/loopback/link-local traffic stays direct even with a TUN VPN.
        val directIps = root.getJSONObject("routing")
            .getJSONArray("rules").getJSONObject(0)
            .getJSONArray("ip")
        val directIpSet = (0 until directIps.length()).map { directIps.getString(it) }.toSet()
        assertTrue("127.0.0.0/8" in directIpSet)
    }

    @Test
    fun buildsShadowsocksOutbound() {
        val p = Profile(name = "SS", protocol = Protocol.SHADOWSOCKS).apply {
            address = "ss.example.com"
            port = 8388
            fields["method"] = "aes-256-gcm"
            fields["password"] = "secret"
        }
        val outbound = XrayConfigBuilder.buildOutbound(p)
        assertEquals("shadowsocks", outbound.getString("protocol"))
        val server = outbound.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("ss.example.com", server.getString("address"))
        assertEquals("aes-256-gcm", server.getString("method"))
        assertEquals("secret", server.getString("password"))
    }

    @Test
    fun rejectsWireGuardAndOpenVpnProtocols() {
        val wg = Profile(name = "WG", protocol = Protocol.WIREGUARD)
        assertThrows(IllegalArgumentException::class.java) { XrayConfigBuilder.build(wg) }
        val ovpn = Profile(name = "OpenVPN", protocol = Protocol.OPENVPN)
        assertThrows(IllegalArgumentException::class.java) { XrayConfigBuilder.build(ovpn) }
    }
}
