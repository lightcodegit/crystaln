package com.crystaln.vpn.data

import com.crystaln.vpn.xray.XrayConfigBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Coverage for the desktop feature batch: split tunneling, connectivity
 * check settings, auto-reconnect knobs, monthly limit + daily history and
 * the crystaln/3 settings-only backup. */
class NewFeaturesTest {

    private fun tmp(): File {
        val dir = File.createTempFile("crystaln-new", ".dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    private fun vless(): Profile =
        Profile(name = "n", protocol = Protocol.VLESS, address = "v.io", port = 443)
            .apply {
                fields["uuid"] = "uuid-1"
                fields["tls"] = "true"
                fields["sni"] = "v.io"
            }

    private fun splitOptions(mode: SettingsStore.SplitTunnelMode) = XrayConfigBuilder.Options(
        splitTunnelMode = mode,
        splitTunnelRules = listOf("example.com", "10.0.0.0/8"),
    )

    private fun routeTags(cfg: String): List<Pair<String, String>> {
        val rules = JSONObject(cfg).getJSONObject("routing").getJSONArray("rules")
        return (0 until rules.length()).map {
            val r = rules.getJSONObject(it)
            val matches = listOfNotNull(
                r.optString("domain").takeIf { d -> d.isNotEmpty() },
                r.optJSONArray("ip")?.let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } },
                r.optString("network").takeIf { n -> n.isNotEmpty() },
                r.optString("port").takeIf { p -> p.isNotEmpty() },
            )
            r.getString("outboundTag") to matches.joinToString(" ")
        }
    }

    @Test
    fun `split tunnel exclude puts listed hosts direct before the proxy catch-all`() {
        val tags = routeTags(
            XrayConfigBuilder.buildProxy(vless(), splitOptions(SettingsStore.SplitTunnelMode.EXCLUDE), 10808, 0),
        )
        val direct = tags.indexOfFirst { (t, m) -> t == "direct" && m.contains("example.com") }
        val catchAll = tags.indexOfFirst { (t, m) -> t == "proxy" && m.contains("tcp,udp") }
        assertTrue("exclude rule present", direct >= 0)
        assertTrue("proxy catch-all present", catchAll >= 0)
        assertTrue("rule must win over catch-all", direct < catchAll)
    }

    @Test
    fun `split tunnel only proxies listed hosts with a direct catch-all`() {
        val tags = routeTags(
            XrayConfigBuilder.buildProxy(vless(), splitOptions(SettingsStore.SplitTunnelMode.ONLY), 10808, 0),
        )
        val proxyRule = tags.indexOfFirst { (t, m) -> t == "proxy" && m.contains("example.com") }
        val directCatchAll = tags.indexOfFirst { (t, m) -> t == "direct" && m.contains("tcp,udp") }
        assertTrue(proxyRule >= 0)
        assertTrue(directCatchAll >= 0)
        assertTrue(proxyRule < directCatchAll)
    }

    @Test
    fun `split tunnel off adds no rules`() {
        val off = routeTags(
            XrayConfigBuilder.buildProxy(vless(), splitOptions(SettingsStore.SplitTunnelMode.OFF), 10808, 0),
        )
        assertFalse(off.any { (_, m) -> m.contains("example.com") })
        assertFalse(off.any { (_, m) -> m.contains("tcp,udp") })
    }

    @Test
    fun `new settings defaults`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        assertTrue(SettingsStore.checkUrl(dir).startsWith("https://"))
        assertEquals(15, SettingsStore.checkTimeoutSec(dir))
        assertFalse(SettingsStore.autoReconnect(dir))
        assertNull(SettingsStore.fallbackProfileId(dir))
        assertEquals(0, SettingsStore.trafficLimitMb(dir))
        assertEquals(SettingsStore.SplitTunnelMode.OFF, SettingsStore.splitTunnelMode(dir))
        assertEquals(emptyList<String>(), SettingsStore.splitTunnelRules(dir))
    }

    @Test
    fun `new settings validate and persist across reload`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setCheckUrl(dir, "http://example.com/cn")
        SettingsStore.setCheckUrl(dir, "not a url") // must be ignored
        SettingsStore.setCheckTimeoutSec(dir, 999)
        SettingsStore.setAutoReconnect(dir, true)
        SettingsStore.setFallbackProfileId(dir, "prof-1")
        SettingsStore.setTrafficLimitMb(dir, -5)
        SettingsStore.setSplitTunnelMode(dir, SettingsStore.SplitTunnelMode.EXCLUDE)
        SettingsStore.setSplitTunnelRules(dir, listOf(" example.com ", "", "10.0.0.0/8"))

        SettingsStore.resetForTest()
        assertEquals("http://example.com/cn", SettingsStore.checkUrl(dir))
        assertEquals(60, SettingsStore.checkTimeoutSec(dir))
        assertTrue(SettingsStore.autoReconnect(dir))
        assertEquals("prof-1", SettingsStore.fallbackProfileId(dir))
        assertEquals(0, SettingsStore.trafficLimitMb(dir))
        assertEquals(SettingsStore.SplitTunnelMode.EXCLUDE, SettingsStore.splitTunnelMode(dir))
        assertEquals(listOf("example.com", "10.0.0.0/8"), SettingsStore.splitTunnelRules(dir))

        SettingsStore.setFallbackProfileId(dir, null)
        SettingsStore.resetForTest()
        assertNull(SettingsStore.fallbackProfileId(dir))
    }

    @Test
    fun `export and import json round-trips the new settings`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setCheckUrl(dir, "https://cp.cloudflare.com/generate_204")
        SettingsStore.setCheckTimeoutSec(dir, 25)
        SettingsStore.setAutoReconnect(dir, true)
        SettingsStore.setFallbackProfileId(dir, "fb")
        SettingsStore.setTrafficLimitMb(dir, 1234)
        SettingsStore.setSplitTunnelMode(dir, SettingsStore.SplitTunnelMode.ONLY)
        SettingsStore.setSplitTunnelRules(dir, listOf("rutube.ru"))
        val json = SettingsStore.exportJson(dir)

        SettingsStore.resetForTest()
        SettingsStore.importJson(dir, json)
        assertEquals("https://cp.cloudflare.com/generate_204", SettingsStore.checkUrl(dir))
        assertEquals(25, SettingsStore.checkTimeoutSec(dir))
        assertTrue(SettingsStore.autoReconnect(dir))
        assertEquals("fb", SettingsStore.fallbackProfileId(dir))
        assertEquals(1234, SettingsStore.trafficLimitMb(dir))
        assertEquals(SettingsStore.SplitTunnelMode.ONLY, SettingsStore.splitTunnelMode(dir))
        assertEquals(listOf("rutube.ru"), SettingsStore.splitTunnelRules(dir))
    }

    @Test
    fun `crystaln3 backup restores settings without touching profiles`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        ProfileStore.resetForTest()
        val p = vless().apply { name = "keep-me" }
        ProfileStore.upsert(dir, p)
        SettingsStore.setTrafficLimitMb(dir, 500)
        SettingsStore.setSplitTunnelRules(dir, listOf("example.com"))

        val pack = Backup.exportSettingsOnly(dir)
        assertTrue(pack.contains("crystaln/3"))
        assertFalse(pack.contains("keep-me")) // profiles are NOT in the pack

        SettingsStore.resetForTest()
        SettingsStore.setTrafficLimitMb(dir, 7)
        SettingsStore.setSplitTunnelRules(dir, emptyList())
        val summary = Backup.importSettingsOnly(dir, pack)
        assertNotNull(summary)
        assertEquals(500, SettingsStore.trafficLimitMb(dir))
        assertEquals(listOf("example.com"), SettingsStore.splitTunnelRules(dir))
        ProfileStore.resetForTest()
        assertEquals(listOf("keep-me"), ProfileStore.load(dir).map { it.name })
        assertNull(Backup.importSettingsOnly(dir, "{\"format\":\"crystaln/2\"}"))
    }

    @Test
    fun `daily history accumulates per day and month total follows it`() {
        val dir = tmp()
        TrafficStatsStore.resetForTest()
        TrafficStatsStore.add(dir, "a", rx = 1_000, tx = 500)
        TrafficStatsStore.add(dir, "b", rx = 2_000, tx = 0)
        TrafficStatsStore.flush(dir)

        val history = TrafficStatsStore.dailyHistory(dir)
        assertEquals(1, history.size)
        assertEquals(LocalDate.now(), history.first().date)
        assertEquals(3_500L, history.first().rx + history.first().tx)

        val month = TrafficStatsStore.monthTotal(dir)
        assertEquals(3_000L, month.rx)
        assertEquals(500L, month.tx)

        // Reload from disk keeps the day bucket.
        TrafficStatsStore.resetForTest()
        assertEquals(1, TrafficStatsStore.dailyHistory(dir).size)
        TrafficStatsStore.add(dir, "a", rx = 5, tx = 5)
        assertEquals(3_510L, TrafficStatsStore.monthTotal(dir).let { it.rx + it.tx })
    }

    @Test
    fun `probe through tunnel fails fast on a closed port`() {
        val started = System.currentTimeMillis()
        // Port 1 is reserved: the socket is refused instantly, the deadline
        // still bounds the whole attempt.
        val ok = com.crystaln.vpn.desktop.net.probeThroughTunnel(
            "http://127.0.0.1:204",
            socksPort = 1,
            timeoutSec = 1,
        )
        assertFalse(ok)
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }
}
