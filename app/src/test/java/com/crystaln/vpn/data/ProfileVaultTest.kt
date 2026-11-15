package com.crystaln.vpn.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The vault is the last line of defense for exported server credentials, so
 * round-trip fidelity and password rejection must both be exact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileVaultTest {

    @Test
    fun roundTripPreservesPayload() {
        val payload = Profile(name = "VLESS", protocol = Protocol.VLESS).apply {
            address = "vless.example.com"
            port = 443
            fields["uuid"] = "secret-uuid"
        }.let { ProfileImporter.exportJson(listOf(it), selectedId = null) }

        val envelope = ProfileVault.encrypt(payload, "correct horse".toCharArray())
        val decrypted = ProfileVault.decrypt(envelope, "correct horse".toCharArray())
        assertEquals(payload, decrypted)
    }

    @Test
    fun wrongPasswordIsRejectedWithoutLeaks() {
        val envelope = ProfileVault.encrypt("top secret", "password1".toCharArray())
        // One wrong char → same opaque VaultException, no details.
        assertThrows(ProfileVault.VaultException::class.java) {
            ProfileVault.decrypt(envelope, "password2".toCharArray())
        }
        // Tampered ciphertext also fails authentication.
        val damaged = envelope.replaceRange(60, 64, "AAAA")
        assertThrows(ProfileVault.VaultException::class.java) {
            ProfileVault.decrypt(damaged, "password1".toCharArray())
        }
    }

    @Test
    fun envelopeIsSelfDescribing() {
        val envelope = ProfileVault.encrypt("data", "pw".toCharArray())
        assertTrue(ProfileVault.isVault(envelope))
        assertFalse(ProfileVault.isVault("vless://uuid@example.com:443"))
        assertFalse(ProfileVault.isVault("{\"profiles\":[]}"))

        val marker = JSONObject(envelope).getJSONObject("crystalnVault")
        assertEquals(1, marker.getInt("v"))
        assertEquals("PBKDF2WithHmacSHA256", marker.getString("kdf"))
        assertTrue(marker.getInt("iters") >= 100_000)
    }

    @Test
    fun emptyPasswordIsRejectedAtEncryptTime() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileVault.encrypt("data", charArrayOf())
        }
    }
}
