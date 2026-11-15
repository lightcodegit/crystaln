package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsStoreTest {

    private fun tmp(): File {
        val dir = File.createTempFile("crystaln-settings", ".dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    @Test
    fun `defaults`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        assertEquals(10808, SettingsStore.socksPort(dir))
        assertEquals(10809, SettingsStore.httpPort(dir))
        assertEquals("1.1.1.1", SettingsStore.dnsServer(dir))
        assertFalse(SettingsStore.dnsViaTunnel(dir))
        assertFalse(SettingsStore.blockAds(dir))
        assertFalse(SettingsStore.autoConnect(dir))
        assertEquals(SettingsStore.TunnelMode.TUN, SettingsStore.tunnelMode(dir))
    }

    @Test
    fun `setters persist to disk across reload`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setSocksPort(dir, 2080)
        SettingsStore.setHttpPort(dir, 2081)
        SettingsStore.setDnsViaTunnel(dir, true)
        SettingsStore.setDnsServer(dir, "9.9.9.9")
        SettingsStore.setBlockAds(dir, true)
        SettingsStore.setAutoConnect(dir, true)
        SettingsStore.setTunnelMode(dir, SettingsStore.TunnelMode.PROXY)

        SettingsStore.resetForTest()
        assertEquals(2080, SettingsStore.socksPort(dir))
        assertEquals(2081, SettingsStore.httpPort(dir))
        assertTrue(SettingsStore.dnsViaTunnel(dir))
        assertEquals("9.9.9.9", SettingsStore.dnsServer(dir))
        assertTrue(SettingsStore.blockAds(dir))
        assertTrue(SettingsStore.autoConnect(dir))
        assertEquals(SettingsStore.TunnelMode.PROXY, SettingsStore.tunnelMode(dir))
    }

    @Test
    fun `ports are coerced into the valid range`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setSocksPort(dir, 80) // privileged port → clamped
        assertTrue(SettingsStore.socksPort(dir) >= 1024)
        SettingsStore.setSocksPort(dir, 99999)
        assertTrue(SettingsStore.socksPort(dir) <= 65535)
    }

    @Test
    fun `settings file is human readable json`() {
        val dir = tmp()
        SettingsStore.resetForTest()
        SettingsStore.setBlockAds(dir, true)
        val text = File(dir, "settings.json").readText()
        assertTrue(text.contains("\"blockAds\": true"))
    }
}
