package com.crystaln.vpn.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject

/** crystaln/2 backups must restore settings, subscriptions and profiles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Context
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()
    }

    @Test
    fun exportImportRoundtrip() {
        val vless = Profile(name = "VLESS A", protocol = Protocol.VLESS).apply {
            address = "a.example.com"
            port = 443
            fields["uuid"] = "uuid-aaa"
        }
        ProfileStore.mergeProfiles(context, listOf(vless), subscriptionId = null, selectFirst = true)

        val sub = SubscriptionsStore.add(context, "https://sub.example.com/list")

        SettingsStore.setThemeMode(context, SettingsStore.ThemeMode.DARK)
        SettingsStore.setAutoConnect(context, true)
        SettingsStore.setDnsViaTunnel(context, true)
        SettingsStore.setDnsServer(context, "9.9.9.9")
        SettingsStore.setBlockAds(context, true)

        val backup = Backup.exportFull(context)
        // Android's JSON stringifier escapes "/" as "\/", so check via parse.
        assertEquals("crystaln/2", JSONObject(backup).optString("format"))

        // Wipe state, then restore.
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()

        val summary = Backup.importFull(context, backup)
        assertTrue(summary != null)

        val restored = ProfileStore.load(context)
        assertEquals(1, restored.size)
        assertEquals("a.example.com", restored[0].address)
        assertEquals("uuid-aaa", restored[0].fields["uuid"])

        val subs = SubscriptionsStore.load(context)
        assertEquals(1, subs.size)
        assertEquals(sub.url, subs[0].url)
        assertTrue(subs[0].enabled)

        assertEquals(SettingsStore.ThemeMode.DARK, SettingsStore.themeMode(context))
        assertTrue(SettingsStore.autoConnect(context))
        assertTrue(SettingsStore.dnsViaTunnel(context))
        assertEquals("9.9.9.9", SettingsStore.dnsServer(context))
        assertTrue(SettingsStore.blockAds(context))
    }

    @Test
    fun nonBackupTextReturnsNull() {
        val link = "vless://uuid@host:443#test"
        assertEquals(null, Backup.importFull(context, link))
        assertEquals(null, Backup.importFull(context, "{\"format\":\"crystaln/1\"}"))
    }

    @Test
    fun subscriptionImportNormalizesAndDedupes() {
        val arr = JSONArray()
            .put(JSONObject().put("url", "https://x.example.com/s").put("enabled", false))
            .put(JSONObject().put("url", "https://x.example.com/s").put("enabled", true))
            .put(JSONObject().put("url", "ftp://bad-url").put("enabled", true))
        assertEquals(2, SubscriptionsStore.importJson(context, arr))
        val subs = SubscriptionsStore.load(context)
        assertEquals(1, subs.size)
        // The last row for the same URL wins.
        assertEquals(true, subs[0].enabled)
    }
}
