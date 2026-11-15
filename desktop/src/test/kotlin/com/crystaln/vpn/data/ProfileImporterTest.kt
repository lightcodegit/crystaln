package com.crystaln.vpn.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop port of the Android universal-import tests (pure JVM). */
class ProfileImporterTest {

    @Test
    fun `json export roundtrip`() {
        val p = Profile(name = "Imported", protocol = Protocol.VLESS, address = "h.io", port = 443)
        p.fields["uuid"] = "u-1"
        val exported = ProfileImporter.exportJson(listOf(p), p.id)

        val parsed = ProfileImporter.parseText(exported)
        assertEquals(1, parsed.size)
        assertEquals("Imported", parsed[0].name)
        assertEquals(Protocol.VLESS, parsed[0].protocol)
        assertEquals("u-1", parsed[0].fields["uuid"])
    }

    @Test
    fun `wireguard config import`() {
        val text = """
            [Interface]
            PrivateKey = AAAAprivatekey=
            Address = 10.8.0.2/32
            DNS = 1.1.1.1

            [Peer]
            PublicKey = BBBBpub=
            Endpoint = wg.example.com:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
        val p = ProfileImporter.parseText(text).single()
        assertEquals(Protocol.WIREGUARD, p.protocol)
        assertTrue(p.wgConfig.contains("[Interface]"))
        assertTrue(p.wgConfig.contains("Endpoint = wg.example.com:51820"))
        assertEquals("wg.example.com", p.address)
        assertEquals(51820, p.port)
    }

    @Test
    fun `openvpn config import`() {
        val text = """
            client
            dev tun
            proto udp
            remote ovpn.example.com 1194
            verb 3
            <ca>
            -----BEGIN CERTIFICATE-----
            MIIB
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        val p = ProfileImporter.parseText(text).single()
        assertEquals(Protocol.OPENVPN, p.protocol)
        assertTrue(p.ovpnConfig.contains("remote ovpn.example.com 1194"))
        assertEquals("ovpn.example.com", p.address)
    }

    @Test
    fun `share link list import`() {
        val parsed = ProfileImporter.parseText(
            "trojan://pw@t.io:443#t1\nvless://id@v.io:443#t2",
        )
        assertEquals(2, parsed.size)
    }

    @Test
    fun `unknown text yields empty`() {
        assertTrue(ProfileImporter.parseText("hello world").isEmpty())
    }

    @Test
    fun `subscription url text is not mistaken for a profile`() {
        // A bare URL body must not produce a bogus HTTP profile with a path.
        assertNull(ShareLinkParser.parse("https://example.com/downloads/list.txt"))
    }
}
