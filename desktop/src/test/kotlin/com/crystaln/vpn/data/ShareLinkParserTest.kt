package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Desktop port of the Android share-link parser tests (pure JVM). */
class ShareLinkParserTest {

    @Test
    fun `vless with reality params`() {
        val p = ShareLinkParser.parse(
            "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
                "?security=reality&type=grpc&fp=chrome&pbk=PUBKEY&sid=abcd&sni=example.com" +
                "#%D0%9C%D0%BE%D0%B9%20%D1%81%D0%B5%D1%80%D0%B2%D0%B5%D1%80",
        )
        assertNotNull(p)
        assertEquals(Protocol.VLESS, p!!.protocol)
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("11111111-2222-3333-4444-555555555555", p.fields["uuid"])
        assertEquals("grpc", p.fields["network"])
        assertEquals("true", p.fields["reality"])
        assertEquals("chrome", p.fields["fingerprint"])
        assertEquals("PUBKEY", p.fields["publicKey"])
        assertEquals("abcd", p.fields["shortId"])
        assertEquals("example.com", p.fields["sni"])
        assertEquals("Мой сервер", p.name)
    }

    @Test
    fun `vless tls via security param`() {
        val p = ShareLinkParser.parse("vless://uuid@h.io:8443?security=tls&type=ws&path=%2Fray")
        assertNotNull(p)
        assertEquals("true", p!!.fields["tls"])
        assertEquals("ws", p.fields["network"])
        assertEquals("/ray", p.fields["path"])
    }

    @Test
    fun trojan() {
        val p = ShareLinkParser.parse("trojan://pass@example.com:443#MyTrojan")
        assertNotNull(p)
        assertEquals(Protocol.TROJAN, p!!.protocol)
        assertEquals("pass", p.fields["password"])
        assertEquals("true", p.fields["tls"])
        assertEquals("MyTrojan", p.name)
    }

    @Test
    fun `shadowsocks SIP002`() {
        // base64url("aes-256-gcm:secretpass") without padding
        val cred = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("aes-256-gcm:secretpass".toByteArray())
        val p = ShareLinkParser.parse("ss://$cred@1.2.3.4:8388#SS%20Node")
        assertNotNull(p)
        assertEquals(Protocol.SHADOWSOCKS, p!!.protocol)
        assertEquals("aes-256-gcm", p.fields["method"])
        assertEquals("secretpass", p.fields["password"])
        assertEquals(8388, p.port)
        assertEquals("SS Node", p.name)
    }

    @Test
    fun `shadowsocks legacy base64`() {
        val body = Base64.getEncoder()
            .encodeToString("chacha20-ietf-poly1305:pw@5.6.7.8:443".toByteArray())
        val p = ShareLinkParser.parse("ss://$body")
        assertNotNull(p)
        assertEquals("chacha20-ietf-poly1305", p!!.fields["method"])
        assertEquals("pw", p.fields["password"])
        assertEquals(443, p.port)
    }

    @Test
    fun vmess() {
        val json = org.json.JSONObject()
            .put("ps", "Мой VMess")
            .put("add", "vm.example.net")
            .put("port", 443)
            .put("id", "aaaa-bbbb")
            .put("aid", 0)
            .put("net", "ws")
            .put("host", "vm.example.net")
            .put("path", "/ws")
            .put("tls", "tls")
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toString().toByteArray())
        val p = ShareLinkParser.parse(link)
        assertNotNull(p)
        assertEquals(Protocol.VMESS, p!!.protocol)
        assertEquals("vm.example.net", p.address)
        assertEquals(443, p.port)
        assertEquals("aaaa-bbbb", p.fields["uuid"])
        assertEquals("0", p.fields["alterId"])
        assertEquals("ws", p.fields["network"])
        assertEquals("/ws", p.fields["path"])
        assertEquals("true", p.fields["tls"])
        assertEquals("Мой VMess", p.name)
    }

    @Test
    fun `socks5 with user`() {
        val p = ShareLinkParser.parse("socks5://user:secret@10.0.0.1:1080#локальный")
        assertNotNull(p)
        assertEquals(Protocol.SOCKS, p!!.protocol)
        assertEquals("10.0.0.1", p.address)
        assertEquals(1080, p.port)
        assertEquals("user", p.fields["username"])
        assertEquals("secret", p.fields["password"])
    }

    @Test
    fun `http proxy link`() {
        val p = ShareLinkParser.parse("http://u:p@proxy.example.io:8080#HttpNode")
        assertNotNull(p)
        assertEquals(Protocol.HTTP, p!!.protocol)
        assertEquals(8080, p.port)
        assertEquals("u", p.fields["username"])
    }

    @Test
    fun `plain web url is not a proxy`() {
        assertNull(ShareLinkParser.parse("https://example.com/some/page?x=1"))
    }

    @Test
    fun `garbage line returns null`() {
        assertNull(ShareLinkParser.parse("not a link"))
        assertNull(ShareLinkParser.parse("vless://@:0"))
    }

    @Test
    fun `parseAll skips unknown lines`() {
        val list = ShareLinkParser.parseAll(
            """
            мусорная строка
            trojan://pw@t.io:443#t
            vless://id@v.io:443#v
            """.trimIndent(),
        )
        assertEquals(2, list.size)
    }

    @Test
    fun `ipv6 host in brackets`() {
        val p = ShareLinkParser.parse("vless://uuid@[2001:db8::1]:443#v6")
        assertNotNull(p)
        assertEquals("2001:db8::1", p!!.address)
        assertEquals(443, p.port)
    }
}
