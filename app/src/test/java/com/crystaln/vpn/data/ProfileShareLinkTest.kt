package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sharing must roundtrip through [ShareLinkParser]: what the app exports has
 * to be importable by the app itself and by any v2rayN-compatible client.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileShareLinkTest {

    @Test
    fun vlessRoundtripsThroughParser() {
        val original = Profile(name = "My VLESS", protocol = Protocol.VLESS).apply {
            address = "example.com"
            port = 443
            fields["uuid"] = "8f4c8d2a-1111-2222-3333-444455556666"
            fields["network"] = "ws"
            fields["path"] = "/ray"
            fields["host"] = "cdn.example.com"
            fields["tls"] = "true"
            fields["sni"] = "sni.example.com"
        }
        val link = ProfileShareLink.shareText(original)
        assertNotNull(link)
        assertTrue(link!!.startsWith("vless://"))

        val parsed = ShareLinkParser.parse(link)!!
        assertEquals(original.protocol, parsed.protocol)
        assertEquals(original.address, parsed.address)
        assertEquals(original.port, parsed.port)
        assertEquals(original.fields["uuid"], parsed.fields["uuid"])
        assertEquals(original.fields["network"], parsed.fields["network"])
        assertEquals(original.fields["path"], parsed.fields["path"])
        assertEquals(original.fields["host"], parsed.fields["host"])
        assertEquals(original.fields["sni"], parsed.fields["sni"])
        assertEquals("true", parsed.fields["tls"])
        assertEquals("My VLESS", parsed.name)
    }

    @Test
    fun trojanAndShadowsocksRoundtrip() {
        val trojan = Profile(name = "Trojan #1", protocol = Protocol.TROJAN).apply {
            address = "t.example.com"
            port = 443
            fields["password"] = "hunter2"
            fields["tls"] = "true"
        }
        val t = ShareLinkParser.parse(ProfileShareLink.shareText(trojan)!!)!!
        assertEquals(Protocol.TROJAN, t.protocol)
        assertEquals("hunter2", t.fields["password"])
        assertEquals("t.example.com", t.address)

        val ss = Profile(name = "SS", protocol = Protocol.SHADOWSOCKS).apply {
            address = "1.2.3.4"
            port = 8388
            fields["method"] = "aes-256-gcm"
            fields["password"] = "pass:word"
        }
        val s = ShareLinkParser.parse(ProfileShareLink.shareText(ss)!!)!!
        assertEquals(Protocol.SHADOWSOCKS, s.protocol)
        assertEquals("aes-256-gcm", s.fields["method"])
        assertEquals("pass:word", s.fields["password"])
        assertEquals("1.2.3.4", s.address)
        assertEquals(8388, s.port)
    }

    @Test
    fun vmessRoundtrips() {
        val vmess = Profile(name = "VMess X", protocol = Protocol.VMESS).apply {
            address = "v.example.com"
            port = 2052
            fields["uuid"] = "uuid-1234"
            fields["alterId"] = "0"
            fields["network"] = "tcp"
        }
        val link = ProfileShareLink.shareText(vmess)!!
        assertTrue(link.startsWith("vmess://"))
        val parsed = ShareLinkParser.parse(link)!!
        assertEquals(Protocol.VMESS, parsed.protocol)
        assertEquals("v.example.com", parsed.address)
        assertEquals(2052, parsed.port)
        assertEquals("uuid-1234", parsed.fields["uuid"])
        assertEquals("VMess X", parsed.name)
    }

    @Test
    fun wireGuardSharesRawConfig() {
        val wg = Profile(name = "WG", protocol = Protocol.WIREGUARD).apply {
            address = "1.2.3.4"
            port = 51820
            wgConfig = "[Interface]\nPrivateKey = abc\n[Peer]\nEndpoint = 1.2.3.4:51820\n"
        }
        assertEquals(wg.wgConfig, ProfileShareLink.shareText(wg))
    }

    @Test
    fun incompleteProfilesShareNull() {
        val empty = Profile(name = "no creds", protocol = Protocol.VLESS).apply {
            address = "example.com"
            port = 443
        }
        assertNull(ProfileShareLink.shareText(empty))

        val noAddress = Profile(name = "no host", protocol = Protocol.TROJAN).apply {
            fields["password"] = "pw"
        }
        assertNull(ProfileShareLink.shareText(noAddress))
    }
}
