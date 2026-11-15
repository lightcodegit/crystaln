package com.crystaln.vpn.xray

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Anti-leak options must translate into dns-out outbound and routing rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XrayConfigBuilderPrivacyTest {

    private fun profile() = Profile(
        name = "vless-test",
        protocol = Protocol.VLESS,
        address = "example.com",
        port = 443,
    ).apply { fields["uuid"] = "8f4c8d2a-1111-2222-3333-444455556666" }

    private fun parse(json: String): JSONObject = JSONObject(json)

    @Test
    fun dnsViaTunnelAddsDnsOutAndRule() {
        val root = parse(
            XrayConfigBuilder.build(
                profile(),
                XrayConfigBuilder.Options(dnsViaTunnel = true, dnsServer = "9.9.9.9"),
            ),
        )
        val outbounds = root.getJSONArray("outbounds")
        var dnsOut: JSONObject? = null
        for (i in 0 until outbounds.length()) {
            val o = outbounds.getJSONObject(i)
            if (o.optString("tag") == "dns-out") dnsOut = o
        }
        assertTrue(dnsOut != null)
        assertEquals("dns", dnsOut!!.getString("protocol"))
        assertEquals("9.9.9.9", dnsOut.getJSONObject("settings").getString("address"))

        val rules = root.getJSONObject("routing").getJSONArray("rules")
        var found = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optString("outboundTag") == "dns-out") {
                assertEquals("53", r.getString("port"))
                found = true
            }
        }
        assertTrue("port-53 rule missing", found)
    }

    @Test
    fun defaultOptionsHaveNoDnsOut() {
        val root = parse(XrayConfigBuilder.build(profile()))
        val outbounds = root.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            assertFalse(outbounds.getJSONObject(i).optString("tag") == "dns-out")
        }
    }

    @Test
    fun blockAdsRuleOnlyWhenEnabled() {
        val off = parse(
            XrayConfigBuilder.build(profile(), XrayConfigBuilder.Options(dnsViaTunnel = true)),
        )
        assertFalse(hasAdRule(off))

        val on = parse(
            XrayConfigBuilder.build(
                profile(),
                XrayConfigBuilder.Options(dnsViaTunnel = true, blockAds = true),
            ),
        )
        assertTrue(hasAdRule(on))
    }

    private fun hasAdRule(root: JSONObject): Boolean {
        val rules = root.getJSONObject("routing").getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            if (r.optString("outboundTag") != "block") continue
            val domains = r.optJSONArray("domain") ?: continue
            for (j in 0 until domains.length()) {
                if (domains.getString(j) == "geosite:category-ads-all") return true
            }
        }
        return false
    }
}
