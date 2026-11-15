package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the Android-parity data layer added with the glass UI:
 * share links, the crystaln/2 backup, traffic stats and store
 * export/import used by the backup.
 */
class DesktopParityDataTest {

    private fun tmp(): File {
        val dir = File.createTempFile("crystaln-parity", ".dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    // ── ProfileShareLink ↔ ShareLinkParser roundtrip ─────────────────────

    @Test
    fun `vless share link roundtrips through the parser`() {
        val p = Profile(
            name = "VLESS тест",
            protocol = Protocol.VLESS,
            address = "example.com",
            port = 443,
        ).apply {
            fields["uuid"] = "0f0f0f0f-1111-2222-3333-444455556666"
            fields["network"] = "ws"
            fields["tls"] = "true"
            fields["sni"] = "cdn.example.com"
            fields["path"] = "/tube"
        }
        val link = ProfileShareLink.shareText(p)
        assertNotNull(link)
        val parsed = ShareLinkParser.parse(link!!)!!
        assertEquals(Protocol.VLESS, parsed.protocol)
        assertEquals("example.com", parsed.address)
        assertEquals(443, parsed.port)
        assertEquals(p.fields["uuid"], parsed.fields["uuid"])
        assertEquals("ws", parsed.fields["network"])
        assertEquals("cdn.example.com", parsed.fields["sni"])
        assertEquals("/tube", parsed.fields["path"])
    }

    @Test
    fun `trojan and shadowsocks share links roundtrip`() {
        val trojan = Profile(
            name = "Trojan",
            protocol = Protocol.TROJAN,
            address = "1.2.3.4",
            port = 443,
        ).apply { fields["password"] = "s3cretPw" }
        val t = ShareLinkParser.parse(ProfileShareLink.shareText(trojan)!!)!!
        assertEquals(Protocol.TROJAN, t.protocol)
        assertEquals("s3cretPw", t.fields["password"])
        assertEquals("1.2.3.4", t.address)

        val ss = Profile(
            name = "SS",
            protocol = Protocol.SHADOWSOCKS,
            address = "5.6.7.8",
            port = 8388,
        ).apply {
            fields["method"] = "aes-256-gcm"
            fields["password"] = "secret"
        }
        val s = ShareLinkParser.parse(ProfileShareLink.shareText(ss)!!)!!
        assertEquals(Protocol.SHADOWSOCKS, s.protocol)
        assertEquals("aes-256-gcm", s.fields["method"])
        assertEquals("secret", s.fields["password"])
    }

    @Test
    fun `vmess share link roundtrips via v2rayN json`() {
        val p = Profile(
            name = "VMess",
            protocol = Protocol.VMESS,
            address = "vm.example.com",
            port = 10086,
        ).apply {
            fields["uuid"] = "aaaabbbb-cccc-dddd-eeee-ffff00001111"
            fields["alterId"] = "0"
            fields["network"] = "tcp"
        }
        val parsed = ShareLinkParser.parse(ProfileShareLink.shareText(p)!!)!!
        assertEquals(Protocol.VMESS, parsed.protocol)
        assertEquals("vm.example.com", parsed.address)
        assertEquals(10086, parsed.port)
        assertEquals(p.fields["uuid"], parsed.fields["uuid"])
    }

    @Test
    fun `profiles without credentials share nothing`() {
        val empty = Profile(name = "пусто", protocol = Protocol.VLESS)
        assertNull(ProfileShareLink.shareText(empty))
    }

    // ── Backup crystaln/2 ────────────────────────────────────────────────

    @Test
    fun `backup export and import roundtrip settings subscriptions profiles`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()
        SettingsStore.resetForTest()

        val p = Profile(name = "Основной", protocol = Protocol.TROJAN, address = "a.example.com", port = 443)
            .apply { fields["password"] = "pw" }
        ProfileStore.upsert(dir, p)
        ProfileStore.select(dir, p.id)
        SubscriptionsStore.add(dir, "https://sub.example.com/all")
        SettingsStore.setThemeMode(dir, SettingsStore.ThemeMode.DARK)
        SettingsStore.setPaletteId(dir, "ocean")
        SettingsStore.setDnsViaTunnel(dir, true)

        val json = Backup.exportFull(dir)
        assertTrue(json.contains("crystaln/2"))

        // Import into a fresh dir: everything must come back.
        val dir2 = tmp()
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()
        SettingsStore.resetForTest()
        val summary = Backup.importFull(dir2, json)!!
        assertTrue(summary.contains("Профили"))
        assertTrue(summary.contains("Подписки"))

        assertEquals(1, ProfileStore.load(dir2).size)
        assertEquals(p.id, ProfileStore.selectedId)
        assertEquals("Основной", ProfileStore.load(dir2).first().name)
        assertEquals(1, SubscriptionsStore.load(dir2).size)
        assertEquals("https://sub.example.com/all", SubscriptionsStore.load(dir2).first().url)
        assertEquals(SettingsStore.ThemeMode.DARK, SettingsStore.themeMode(dir2))
        assertEquals("ocean", SettingsStore.paletteId(dir2))
        assertTrue(SettingsStore.dnsViaTunnel(dir2))
    }

    @Test
    fun `non-backup text is not treated as a full backup`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        assertNull(Backup.importFull(dir, "vless://uuid@host:443#x"))
        assertNull(Backup.importFull(dir, "not json at all"))
    }

    @Test
    fun `vault wraps a full backup and opens with the password`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()
        SettingsStore.resetForTest()
        ProfileStore.upsert(
            dir,
            Profile(name = "SS", protocol = Protocol.SHADOWSOCKS, address = "h", port = 1)
                .apply {
                    fields["method"] = "aes-256-gcm"
                    fields["password"] = "p"
                },
        )

        val plain = Backup.exportFull(dir)
        val envelope = ProfileVault.encrypt(plain, "пароль".toCharArray())
        assertTrue(ProfileVault.isVault(envelope))
        val decrypted = ProfileVault.decrypt(envelope, "пароль".toCharArray())
        val dir2 = tmp()
        ProfileStore.resetForTest()
        assertNotNull(Backup.importFull(dir2, decrypted))
        assertEquals(1, ProfileStore.load(dir2).size)
    }

    // ── TrafficStatsStore ────────────────────────────────────────────────

    @Test
    fun `traffic stats accumulate and persist across reload`() {
        val dir = tmp()
        TrafficStatsStore.resetForTest()
        TrafficStatsStore.add(dir, "p1", 100, 50, 5)
        TrafficStatsStore.add(dir, "p1", 200, 150, 5)
        TrafficStatsStore.add(dir, "p2", 1, 1, 1)

        var s = TrafficStatsStore.get(dir, "p1")
        assertEquals(300L, s.rx)
        assertEquals(200L, s.tx)
        assertEquals(10L, s.seconds)

        TrafficStatsStore.flush(dir)
        TrafficStatsStore.resetForTest()
        s = TrafficStatsStore.get(dir, "p1")
        assertEquals(300L, s.rx)
        assertEquals(200L, s.tx)
        assertEquals(10L, s.seconds)
    }

    @Test
    fun `traffic stats ignore non-positive deltas`() {
        val dir = tmp()
        TrafficStatsStore.resetForTest()
        TrafficStatsStore.add(dir, "p1", -5, -5, 0)
        assertEquals(0L, TrafficStatsStore.get(dir, "p1").rx)
    }

    @Test
    fun `clearing a profile drops its counters`() {
        val dir = tmp()
        TrafficStatsStore.resetForTest()
        TrafficStatsStore.add(dir, "p1", 100, 100, 3)
        TrafficStatsStore.clear(dir, "p1")
        assertEquals(TrafficStats(), TrafficStatsStore.get(dir, "p1"))
    }

    // ── SettingsStore export/import (crystaln/2 payload) ─────────────────

    @Test
    fun `settings export and import roundtrip`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setThemeMode(dir, SettingsStore.ThemeMode.LIGHT)
        SettingsStore.setPaletteId(dir, "sunset")
        SettingsStore.setAutoConnect(dir, true)
        SettingsStore.setAutoFastest(dir, true)
        SettingsStore.setDnsViaTunnel(dir, true)
        SettingsStore.setDnsServer(dir, "9.9.9.9")
        SettingsStore.setBlockAds(dir, true)

        val json = SettingsStore.exportJson(dir)

        val dir2 = tmp()
        SettingsStore.resetForTest()
        SettingsStore.importJson(dir2, json)
        assertEquals(SettingsStore.ThemeMode.LIGHT, SettingsStore.themeMode(dir2))
        assertEquals("sunset", SettingsStore.paletteId(dir2))
        assertTrue(SettingsStore.autoConnect(dir2))
        assertTrue(SettingsStore.autoFastest(dir2))
        assertTrue(SettingsStore.dnsViaTunnel(dir2))
        assertEquals("9.9.9.9", SettingsStore.dnsServer(dir2))
        assertTrue(SettingsStore.blockAds(dir2))
    }

    // ── SubscriptionsStore export/import ─────────────────────────────────

    @Test
    fun `subscriptions export and import dedupe by url`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        val s1 = SubscriptionsStore.add(dir, "https://a.example.com/one")
        SubscriptionsStore.setStatus(dir, s1.id, success = true)
        SubscriptionsStore.add(dir, "https://b.example.com/two")

        val json = SubscriptionsStore.exportJson(dir)

        val dir2 = tmp()
        SubscriptionsStore.resetForTest()
        val imported = SubscriptionsStore.importJson(dir2, json)
        assertEquals(2, imported)

        // Re-importing the same payload must not stack duplicates.
        val again = SubscriptionsStore.importJson(dir2, json)
        assertEquals(2, again)
        assertEquals(2, SubscriptionsStore.load(dir2).size)
        assertFalse(SubscriptionsStore.load(dir2).first().url.isEmpty())
    }
}
