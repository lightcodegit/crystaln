package com.crystaln.vpn.crypto

import java.math.BigInteger
import java.security.SecureRandom

/**
 * Minimal X25519 (RFC 7748) implementation for REALITY key generation on
 * Android, where no native xray binary is available. Slow but only used once
 * per key generation.
 */
object X25519 {

    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val TWO = BigInteger.valueOf(2)

    private fun bytesToBigInt(b: ByteArray): BigInteger {
        var r = BigInteger.ZERO
        for (i in b.indices) {
            r = r.or(BigInteger.valueOf(b[i].toLong() and 0xFF).shiftLeft(8 * i))
        }
        return r
    }

    private fun bigIntToBytes(x: BigInteger, len: Int): ByteArray {
        val out = ByteArray(len)
        var v = x.mod(P)
        for (i in 0 until len) {
            out[i] = (v and BigInteger.valueOf(0xFF)).toByte()
            v = v.shiftRight(8)
        }
        return out
    }

    private fun clamp(scalar: ByteArray): ByteArray {
        val out = scalar.copyOf()
        out[0] = (out[0].toInt() and 248).toByte()
        out[31] = ((out[31].toInt() and 127) or 64).toByte()
        return out
    }

    /** Montgomery ladder scalar multiplication: returns x-coordinate of k*P(u). */
    private fun scalarMult(scalar: ByteArray, u: ByteArray): ByteArray {
        val k = clamp(scalar)
        val x1 = bytesToBigInt(u)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = (k[t / 8].toInt() shr (t % 8)) and 1
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            val x3n = da.add(cb).mod(P)
            val z3n = x1.multiply(da.subtract(cb).mod(P)).mod(P).multiply(da.subtract(cb).mod(P)).mod(P).mod(P)
            val x2n = aa.multiply(bb).mod(P)
            val z2n = e.multiply(aa.add(BigInteger.valueOf(121665).multiply(e).mod(P).multiply(BigInteger.valueOf(4).modInverse(P))).mod(P)).mod(P)
            x2 = x2n; z2 = z2n; x3 = x3n; z3 = z3n
        }
        if (swap == 1) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }
        val result = x2.multiply(z2.modInverse(P)).mod(P)
        return bigIntToBytes(result, 32)
    }

    /** Generates a keypair. Returns (privateKey, publicKey) as 32-byte arrays. */
    fun generate(): Pair<ByteArray, ByteArray> {
        val rnd = SecureRandom()
        val sk = ByteArray(32).also { rnd.nextBytes(it) }
        val clamped = clamp(sk)
        val pk = scalarMult(clamped, byteArrayOf(9) + ByteArray(31))
        return clamped to pk
    }

    fun toHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}
