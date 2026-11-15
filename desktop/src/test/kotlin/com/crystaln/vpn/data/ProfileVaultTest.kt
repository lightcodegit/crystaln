package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Desktop port of the vault tests: javax.crypto instead of android.util. */
class ProfileVaultTest {

    @Test
    fun `encrypt then decrypt roundtrip`() {
        val secret = "профили с кириллицей и \"кавычками\""
        val envelope = ProfileVault.encrypt(secret, "пароль".toCharArray())
        assertTrue(ProfileVault.isVault(envelope))
        assertEquals(secret, ProfileVault.decrypt(envelope, "пароль".toCharArray()))
    }

    @Test
    fun `wrong password fails`() {
        val envelope = ProfileVault.encrypt("data", "right".toCharArray())
        assertThrows(ProfileVault.VaultException::class.java) {
            ProfileVault.decrypt(envelope, "wrong".toCharArray())
        }
    }

    @Test
    fun `empty password is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProfileVault.encrypt("data", charArrayOf())
        }
    }

    @Test
    fun `isVault is strict`() {
        assertFalse(ProfileVault.isVault("not json"))
        assertFalse(ProfileVault.isVault("{\"other\":1}"))
        assertTrue(ProfileVault.isVault(ProfileVault.encrypt("x", "p".toCharArray())))
    }

    @Test
    fun `envelope is truncated damage safe`() {
        val envelope = ProfileVault.encrypt("x", "p".toCharArray())
        assertThrows(ProfileVault.VaultException::class.java) {
            ProfileVault.decrypt(envelope.dropLast(10), "p".toCharArray())
        }
    }
}
