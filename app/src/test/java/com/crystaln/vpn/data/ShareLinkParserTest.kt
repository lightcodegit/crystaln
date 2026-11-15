package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Share-link parsing is user-facing import: regressions here silently break servers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareLinkParserTest {

    @Test
    fun parsesVlessWithFragmentAndNetwork() {
        val p = ShareLinkParser.parse(
            "vless://uuid@example.com:443?network=ws&path=%2Fpath&host=cdn.example.com&security=tls#My%20Server",
        )
        assertNotNull(p)
        assertEquals("My Server", p!!.name)
        assertEquals(Protocol.VLESS, p.protocol)
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("uuid", p.fields["uuid"])
        assertEquals("ws", p.fields["network"])
        assertEquals("/path", p.fields["path"])
        assertEquals("cdn.example.com", p.fields["host"])
        assertEquals("true", p.fields["tls"])
    }

    @Test
    fun parsesVmessV2rayNJson() {
        val json = """
            {"add":"vm.example.com","port":8443,"id":"vm-uuid","aid":0,
             "net":"tcp","tls":"tls","sni":"sni.example.com","ps":"VMess node"}
        """.trimIndent()
        val b64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
        val p = ShareLinkParser.parse("vmess://$b64")
        assertNotNull(p)
        assertEquals("VMess node", p!!.name)
        assertEquals(Protocol.VMESS, p.protocol)
        assertEquals("vm.example.com", p.address)
        assertEquals(8443, p.port)
        assertEquals("vm-uuid", p.fields["uuid"])
        assertEquals("tcp", p.fields["network"])
        assertEquals("true", p.fields["tls"])
        assertEquals("sni.example.com", p.fields["sni"])
    }

    @Test
    fun parsesSip002Shadowsocks() {
        val cred = android.util.Base64.encodeToString(
            "aes-256-gcm:secret".toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
        val p = ShareLinkParser.parse("ss://$cred@ss.example.com:8388#SS")
        assertNotNull(p)
        assertEquals(Protocol.SHADOWSOCKS, p!!.protocol)
        assertEquals("ss.example.com", p.address)
        assertEquals(8388, p.port)
        assertEquals("aes-256-gcm", p.fields["method"])
        assertEquals("secret", p.fields["password"])
    }

    @Test
    fun parsesTrojanAndSkipsGarbage() {
        val text = """
            not a link
            trojan://pass@trojan.example.com:443#Trojan
        """.trimIndent()
        val parsed = ShareLinkParser.parseAll(text)
        assertEquals(1, parsed.size)
        assertEquals(Protocol.TROJAN, parsed[0].protocol)
        assertEquals("Trojan", parsed[0].name)
        assertEquals("trojan.example.com", parsed[0].address)
        assertEquals("pass", parsed[0].fields["password"])
    }

    @Test
    fun rejectsIncompleteLinks() {
        assertNull(ShareLinkParser.parse("vless://example.com:443"))
        assertNull(ShareLinkParser.parse("vmess://not-base64!!"))
        assertNull(ShareLinkParser.parse("ss://bad"))
    }
}
