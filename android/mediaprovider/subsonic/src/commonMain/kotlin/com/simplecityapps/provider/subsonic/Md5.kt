package com.simplecityapps.provider.subsonic

/**
 * The MD5 digest of [input]'s UTF-8 bytes as lowercase hex (RFC 1321), for Subsonic's token: `md5(password + salt)`.
 * Common code has no `MessageDigest`, and this is the only hash the provider needs.
 */
internal fun md5Hex(input: String): String = md5(input.encodeToByteArray()).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

private val SHIFTS = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

// floor(abs(sin(i + 1)) * 2^32)
private val CONSTANTS = IntArray(64) { i -> (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt() }

private fun md5(message: ByteArray): ByteArray {
    val bitLength = message.size.toLong() * 8
    val paddedLength = ((message.size + 8) / 64 + 1) * 64
    val padded = message.copyOf(paddedLength)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) padded[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476
    val words = IntArray(16)
    for (chunk in 0 until paddedLength step 64) {
        for (i in 0 until 16) {
            val offset = chunk + i * 4
            words[i] = (padded[offset].toInt() and 0xff) or
                ((padded[offset + 1].toInt() and 0xff) shl 8) or
                ((padded[offset + 2].toInt() and 0xff) shl 16) or
                ((padded[offset + 3].toInt() and 0xff) shl 24)
        }
        var a = a0
        var b = b0
        var c = c0
        var d = d0
        for (i in 0 until 64) {
            val f: Int
            val g: Int
            when (i / 16) {
                0 -> {
                    f = (b and c) or (b.inv() and d)
                    g = i
                }

                1 -> {
                    f = (d and b) or (d.inv() and c)
                    g = (5 * i + 1) % 16
                }

                2 -> {
                    f = b xor c xor d
                    g = (3 * i + 5) % 16
                }

                else -> {
                    f = c xor (b or d.inv())
                    g = (7 * i) % 16
                }
            }
            val rotated = (a + f + CONSTANTS[i] + words[g]).rotateLeft(SHIFTS[i])
            a = d
            d = c
            c = b
            b += rotated
        }
        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }
    return ByteArray(16) { i ->
        val word = when (i / 4) {
            0 -> a0
            1 -> b0
            2 -> c0
            else -> d0
        }
        (word ushr (8 * (i % 4))).toByte()
    }
}
