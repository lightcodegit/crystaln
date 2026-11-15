package com.crystaln.vpn.desktop.net

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class WgSetconfTest {

    @Test
    fun stripsWgQuickOnlyKeysAndExtractsNetworkSettings() {
        val result = WgSetconf.build(
            """
            [Interface]
            PrivateKey = private-key
            Address = 10.0.0.2/32, fd00::2/128
            DNS = 1.1.1.1, 2606:4700:4700::1111
            MTU = 1380
            ListenPort = 51820
            Table = 12345
            SaveConfig = true
            PostUp = echo should-not-run

            [Peer]
            PublicKey = peer-key
            PresharedKey = psk
            Endpoint = example.org:51820
            AllowedIPs = 10.0.0.0/24, 192.168.1.2/32
            PersistentKeepalive = 25
            """.trimIndent(),
        )

        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), result.addresses)
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), result.dnsServers)
        assertEquals(1380, result.mtu)
        assertEquals(listOf("10.0.0.0/24", "192.168.1.2/32"), result.routes)
        assertFalse(result.defaultRoute)
        assertTrue(result.setconf.contains("PrivateKey = private-key"))
        assertTrue(result.setconf.contains("ListenPort = 51820"))
        assertTrue(result.setconf.contains("AllowedIPs = 10.0.0.0/24, 192.168.1.2/32"))
        assertTrue(result.setconf.contains("PersistentKeepalive = 25"))
        listOf("Address =", "DNS =", "MTU =", "Table =", "SaveConfig", "PostUp", "echo").forEach {
            assertFalse(result.setconf.contains(it), "wg-quick key leaked: $it")
        }
    }

    @Test
    fun keepsAmneziaJunkParametersInInterfaceIncludingRangesAndObfuscation() {
        val result = WgSetconf.build(
            """
            [Interface]
            PrivateKey = private-key
            I1 = <b 0x0102>
            HeaderProtectionKey = header-key

            [AmneziaWG]
            Jc = 4
            Jmin = 40
            Jmax = 70
            S1 = 15
            S3 = 35
            S4 = 45
            H1 = 123456-123500
            H2 = 2

            [Peer]
            PublicKey = peer-key
            AllowedIPs = 0.0.0.0/0, ::/0, 10.0.0.0/8
            """.trimIndent(),
        )

        assertTrue(result.setconf.contains("Jc = 4"))
        assertTrue(result.setconf.contains("Jmin = 40"))
        assertTrue(result.setconf.contains("Jmax = 70"))
        assertTrue(result.setconf.contains("S1 = 15"))
        assertTrue(result.setconf.contains("S3 = 35"))
        assertTrue(result.setconf.contains("S4 = 45"))
        assertTrue(result.setconf.contains("H1 = 123456-123500"))
        assertTrue(result.setconf.contains("H2 = 2"))
        assertTrue(result.setconf.contains("I1 = <b 0x0102>"))
        assertTrue(result.setconf.contains("HeaderProtectionKey = header-key"))
        assertFalse(result.setconf.contains("[AmneziaWG]"))
        assertTrue(result.defaultRoute)
        assertEquals(listOf("0.0.0.0/0", "::/0", "10.0.0.0/8"), result.routes)
    }

    @Test
    fun explicitProfileFieldsOverrideImportedJunkParameters() {
        val result = WgSetconf.build(
            """
            [Interface]
            PrivateKey = private-key
            Jc = 2
            S1 = 10
            H1 = 1

            [Peer]
            PublicKey = peer-key
            Endpoint = example.org:1234
            AllowedIPs = 10.0.0.0/24
            """.trimIndent(),
            fields = mapOf(
                "jc" to "7",
                "s1" to "0",
                "h1" to "123456-123500",
                "unknown" to "ignored",
            ),
        )

        assertTrue(result.setconf.contains("Jc = 7"))
        assertTrue(result.setconf.contains("H1 = 123456-123500"))
        assertTrue(result.setconf.contains("S1 = 10")) // zero means "not set" in the editor
        assertFalse(result.setconf.contains("Jc = 2"))
        assertFalse(result.setconf.contains("H1 = 1\n"))
        assertEquals(1420, result.mtu)
    }

    @Test
    fun keepsSeveralPeersAndDeduplicatesRoutes() {
        val result = WgSetconf.build(
            """
            [Interface]
            PrivateKey = private-key
            [Peer]
            PublicKey = first
            AllowedIPs = 10.0.0.1/32, fd00::/64
            [Peer]
            PublicKey = second
            AllowedIPs = 10.0.0.1/32, 10.0.0.3/32
            """.trimIndent(),
        )

        assertEquals(2, Regex("\\[Peer]").findAll(result.setconf).count())
        assertEquals(listOf("10.0.0.1/32", "fd00::/64", "10.0.0.3/32"), result.routes)
    }
}
