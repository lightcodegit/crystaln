package com.crystaln.vpn.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The config check is what tells the user "your import is incomplete" before
 * they waste a connection attempt, so its verdicts must be exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectionDiagnosticsTest {

    @Test
    fun completeVlessProfilePasses() {
        val p = Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
            address = "vless.example.com"
            port = 443
            fields["uuid"] = "uuid"
        }
        val checks = ConnectionDiagnostics.staticChecks(p)
        assertTrue(checks.single().ok)
    }

    @Test
    fun vlessWithoutUuidFails() {
        val p = Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
            address = "vless.example.com"
            port = 443
        }
        val r = ConnectionDiagnostics.staticChecks(p).single()
        assertFalse(r.ok)
        assertTrue(r.detail.contains("uuid"))
    }

    @Test
    fun realityRequiresPublicKeyAndShortId() {
        fun realityProfile(pbk: String?, sid: String?): Profile =
            Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
                address = "vless.example.com"
                port = 443
                fields["uuid"] = "uuid"
                fields["reality"] = "true"
                pbk?.let { fields["publicKey"] = it }
                sid?.let { fields["shortId"] = it }
            }

        assertFalse(ConnectionDiagnostics.staticChecks(realityProfile(null, null)).single().ok)
        assertFalse(ConnectionDiagnostics.staticChecks(realityProfile("pbk", null)).single().ok)
        assertTrue(ConnectionDiagnostics.staticChecks(realityProfile("pbk", "sid")).single().ok)
    }

    @Test
    fun shadowsocksNeedsMethodAndPassword() {
        val p = Profile(name = "SS", protocol = Protocol.SHADOWSOCKS).apply {
            address = "ss.example.com"
            port = 8388
            fields["password"] = "pw"
        }
        val r = ConnectionDiagnostics.staticChecks(p).single()
        assertFalse(r.ok)
        assertTrue(r.detail.contains("method"))

        p.fields["method"] = "aes-256-gcm"
        assertTrue(ConnectionDiagnostics.staticChecks(p).single().ok)
    }

    @Test
    fun wireGuardNeedsPrivateKeyPeerAndEndpoint() {
        val p = Profile(name = "WG", protocol = Protocol.WIREGUARD).apply {
            address = "wg.example.com"
            port = 51820
        }
        assertFalse(ConnectionDiagnostics.staticChecks(p).single().ok)

        p.wgConfig = """
            [Interface]
            PrivateKey = key
            [Peer]
            PublicKey = peer
            Endpoint = wg.example.com:51820
        """.trimIndent()
        assertTrue(ConnectionDiagnostics.staticChecks(p).single().ok)
    }

    @Test
    fun openVpnNeedsRemoteAndCa() {
        val p = Profile(name = "OpenVPN", protocol = Protocol.OPENVPN).apply {
            address = "ovpn.example.com"
            port = 1194
            ovpnConfig = "client\nremote ovpn.example.com 1194\n<ca>\nCERT\n</ca>\n"
        }
        assertTrue(ConnectionDiagnostics.staticChecks(p).single().ok)

        p.ovpnConfig = "client\nremote ovpn.example.com 1194\n"
        val r = ConnectionDiagnostics.staticChecks(p).single()
        assertFalse(r.ok)
        assertTrue(r.detail.contains("ca"))
    }

    @Test
    fun missingEndpointFieldsAreReportedTogether() {
        val p = Profile(name = "empty", protocol = Protocol.TROJAN)
        val r = ConnectionDiagnostics.staticChecks(p).single()
        assertFalse(r.ok)
        assertTrue(r.detail.contains("address"))
        assertTrue(r.detail.contains("port"))
        assertTrue(r.detail.contains("password"))
    }
}
