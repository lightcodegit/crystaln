package com.crystaln.vpn.xray

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the desktop config builder. Only [buildProxy] output is valid for
 * the Linux xray-core binary; `build()` (TUN inbound) is kept as a reference
 * for the Android copy and is not exercised here.
 */
class XrayConfigBuilderTest {

    private fun vless(): Profile =
        Profile(name = "n", protocol = Protocol.VLESS, address = "v.io", port = 443)
            .apply {
                fields["uuid"] = "uuid-1"
                fields["tls"] = "true"
                fields["sni"] = "v.io"
            }

    @Test
    fun `proxy config has socks and http inbounds`() {
        val cfg = JSONObject(XrayConfigBuilder.buildProxy(vless(), XrayConfigBuilder.Options(), 10808, 10809))
        val inbounds = cfg.getJSONArray("inbounds")
        assertEquals(2, inbounds.length())

        val socks = inbounds.getJSONObject(0)
        assertEquals("socks", socks.getString("protocol"))
        assertEquals(10808, socks.getInt("port"))
        assertEquals("127.0.0.1", socks.getString("listen"))
        assertTrue(socks.getJSONObject("settings").getBoolean("udp"))

        val http = inbounds.getJSONObject(1)
        assertEquals("http", http.getString("protocol"))
        assertEquals(10809, http.getInt("port"))
    }

    @Test
    fun `httpPort zero disables http inbound`() {
        val cfg = JSONObject(XrayConfigBuilder.buildProxy(vless(), XrayConfigBuilder.Options(), 10808, 0))
        val inbounds = cfg.getJSONArray("inbounds")
        assertEquals(1, inbounds.length())
        assertEquals("socks", inbounds.getJSONObject(0).getString("protocol"))
    }

    @Test
    fun `outbound matches protocol and credentials`() {
        val cfg = JSONObject(XrayConfigBuilder.buildProxy(vless(), XrayConfigBuilder.Options(), 10808, 10809))
        val outbounds = cfg.getJSONArray("outbounds")
        val proxy = outbounds.getJSONObject(0)
        assertEquals("proxy", proxy.getString("tag"))
        assertEquals("vless", proxy.getString("protocol"))
        assertEquals(
            "uuid-1",
            proxy.getJSONObject("settings").getJSONArray("vnext")
                .getJSONObject(0).getJSONArray("users").getJSONObject(0).getString("id"),
        )
        val tags = (0 until outbounds.length()).map { outbounds.getJSONObject(it).optString("tag") }
        assertTrue("direct" in tags)
        assertTrue("block" in tags)
    }

    @Test
    fun `no tun inbound in proxy config`() {
        val cfg = JSONObject(XrayConfigBuilder.buildProxy(vless(), XrayConfigBuilder.Options(), 10808, 10809))
        val inbounds = cfg.getJSONArray("inbounds")
        for (i in 0 until inbounds.length()) {
            assertFalse("tun" == inbounds.getJSONObject(i).optString("protocol"))
        }
    }

    @Test
    fun `blockAds adds geosite rule when geosite is present`() {
        val withAds = XrayConfigBuilder.buildProxy(
            vless(),
            XrayConfigBuilder.Options(blockAds = true),
            10808,
            10809,
        )
        assertTrue(withAds.contains("geosite:category-ads-all"))

        val withoutAds = XrayConfigBuilder.buildProxy(
            vless(),
            XrayConfigBuilder.Options(blockAds = false),
            10808,
            10809,
        )
        assertFalse(withoutAds.contains("geosite"))
    }

    @Test
    fun `dnsViaTunnel adds dns outbound and port 53 rule`() {
        val cfg = XrayConfigBuilder.buildProxy(
            vless(),
            XrayConfigBuilder.Options(dnsViaTunnel = true, dnsServer = "9.9.9.9"),
            10808,
            10809,
        )
        assertTrue(cfg.contains("\"dns-out\""))
        assertTrue(cfg.contains("9.9.9.9"))
        val root = JSONObject(cfg)
        val rules = root.getJSONObject("routing").getJSONArray("rules")
        val dnsRule = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .first { it.optString("outboundTag") == "dns-out" }
        assertEquals("53", dnsRule.getString("port"))
    }

    @Test
    fun `protocol mapping`() {
        val cases = mapOf(
            Protocol.VMESS to "vmess",
            Protocol.VLESS to "vless",
            Protocol.TROJAN to "trojan",
            Protocol.SHADOWSOCKS to "shadowsocks",
            Protocol.SOCKS to "socks",
            Protocol.HTTP to "http",
        )
        cases.forEach { (proto, xrayName) ->
            val p = Profile(name = "x", protocol = proto, address = "a", port = 1)
            val cfg = JSONObject(XrayConfigBuilder.buildProxy(p, XrayConfigBuilder.Options(), 10808, 0))
            assertEquals(xrayName, cfg.getJSONArray("outbounds").getJSONObject(0).getString("protocol"))
        }
    }
}
