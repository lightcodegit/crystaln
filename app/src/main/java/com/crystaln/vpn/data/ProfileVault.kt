package com.crystaln.vpn.data

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected export ("crystaln vault"): AES-256-GCM with a key
 * derived by PBKDF2-HMAC-SHA256. The envelope is a self-describing JSON, so
 * imports can detect it and ask for the password instead of failing to parse.
 *
 * Envelope shape:
 * {"crystalnVault":{"v":1,"kdf":"PBKDF2WithHmacSHA256","iters":N,
 *  "salt":"b64","iv":"b64","data":"b64"}}
 */
object ProfileVault {

    private const val MARKER = "crystalnVault"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERS = 180_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 32
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    /** True when [text] looks like a vault envelope (possibly truncated). */
    fun isVault(text: String): Boolean =
        text.trim().startsWith("{\"$MARKER\"")

    fun encrypt(plain: String, password: CharArray): String {
        require(password.isNotEmpty()) { "empty password" }
        val salt = ByteArray(SALT_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt, ITERS),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put(
                MARKER,
                JSONObject()
                    .put("v", VERSION)
                    .put("kdf", KDF)
                    .put("iters", ITERS)
                    .put("salt", b64(salt))
                    .put("iv", b64(iv))
                    .put("data", b64(data)),
            )
            .toString()
    }

    /** @throws VaultException on a wrong password or a damaged envelope. */
    fun decrypt(envelope: String, password: CharArray): String {
        try {
            val v = JSONObject(envelope.trim()).getJSONObject(MARKER)
            val iters = v.getInt("iters")
            require(iters in 1..5_000_000) { "bad iters" }
            val salt = unb64(v.getString("salt"))
            val iv = unb64(v.getString("iv"))
            val data = unb64(v.getString("data"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt, iters),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            return String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: VaultException) {
            throw e
        } catch (_: Exception) {
            // GCM authentication failure and structural damage both land here;
            // from the user's point of view the password was wrong or the file
            // is broken — never leak which.
            throw VaultException()
        }
    }

    class VaultException : Exception("vault decrypt failed")

    private fun deriveKey(password: CharArray, salt: ByteArray, iters: Int) =
        SecretKeySpec(
            SecretKeyFactory.getInstance(KDF)
                .generateSecret(PBEKeySpec(password, salt, iters, KEY_BITS))
                .encoded,
            "AES",
        )

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray =
        Base64.decode(s, Base64.NO_WRAP)
}
