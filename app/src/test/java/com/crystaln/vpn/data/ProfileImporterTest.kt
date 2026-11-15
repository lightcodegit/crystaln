package com.crystaln.vpn.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Import/export must not lose profiles or accidentally misclassify config formats. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileImporterTest {

    @Test
    fun parsesAmneziaWireGuardConfigWithObfuscation() {
        val config = """
            [Interface]
            PrivateKey = wg-private-key
            Address = 10.2.0.2/32
            DNS = 1.1.1.1
            Jc = 4
            Jmin = 40
            Jmax = 70
            S1 = 116
            S2 = 80

            [Peer]
            PublicKey = wg-public-key
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = awg.example.com:51820
        """.trimIndent()
        val p = ProfileImporter.parseText(config).single()
        assertEquals(Protocol.AMNEZIAWG, p.protocol)
        assertEquals("awg.example.com", p.address)
        assertEquals(51820, p.port)
        assertEquals("4", p.fields["jc"])
        assertEquals("40", p.fields["jmin"])
        assertEquals("70", p.fields["jmax"])
        assertEquals("116", p.fields["s1"])
        assertEquals("80", p.fields["s2"])
        assertTrue(p.wgConfig.contains("Endpoint = awg.example.com:51820"))
    }

    @Test
    fun parsesPlainWireGuardConfig() {
        val config = """
            [Interface]
            PrivateKey = private
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = public
            Endpoint = wg.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val p = ProfileImporter.parseText(config).single()
        assertEquals(Protocol.WIREGUARD, p.protocol)
        assertFalse(p.fields.containsKey("jc"))
    }

    @Test
    fun parsesOpenVpnRemoteAndInlineConfig() {
        val config = """
            client
            dev tun
            proto udp
            remote ovpn.example.com 1194
            verb 3
            <ca>
            -----BEGIN CERTIFICATE-----
            CERT
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        val p = ProfileImporter.parseText(config).single()
        assertEquals(Protocol.OPENVPN, p.protocol)
        assertEquals("ovpn.example.com", p.address)
        assertEquals(1194, p.port)
        assertEquals(config, p.ovpnConfig)
    }

    @Test
    fun jsonExportRoundTripsProfilesAndSelection() {
        val profiles = listOf(
            Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
                address = "vless.example.com"
                port = 443
                fields["uuid"] = "secret-uuid"
                fields["tls"] = "true"
            },
            Profile(name = "OpenVPN", protocol = Protocol.OPENVPN).apply {
                address = "ovpn.example.com"
                port = 1194
                ovpnConfig = "client\nremote ovpn.example.com 1194\n"
            },
        )
        val json = ProfileImporter.exportJson(profiles, selectedId = profiles[1].id)
        val imported = ProfileImporter.parseText(json)
        assertEquals(profiles.size, imported.size)
        assertEquals(profiles[0].address, imported[0].address)
        assertEquals(profiles[0].fields["uuid"], imported[0].fields["uuid"])
        assertEquals(profiles[1].ovpnConfig, imported[1].ovpnConfig)
        assertEquals(profiles[1].id, imported[1].id)
    }

    @Test
    fun rejectsMalformedJsonInsteadOfThrowing() {
        val text = "{\"profiles\":[{\"name\":broken"
        assertTrue(ProfileImporter.parseText(text).isEmpty())
    }

    @Test
    fun malformedWireGuardEndpointIsRejected() {
        val config = """
            [Interface]
            PrivateKey = private

            [Peer]
            Endpoint = wg.example.com
            PublicKey = public
        """.trimIndent()
        val parsed = ProfileImporter.parseText(config)
        assertTrue(parsed.isEmpty())
    }
}
