package com.simplecityapps.shuttle.scrobbling.lastfm

/**
 * Signs a Last.fm API call ([authspec](https://www.last.fm/api/authspec)): every param except `format` and
 * `callback`, sorted by name, concatenated as `namevalue` pairs with no separator, the shared secret appended,
 * then MD5 hex-digested.
 */
object LastFmSigner {
    fun sign(
        params: Map<String, String>,
        sharedSecret: String
    ): String {
        val signable = params
            .filterKeys { it != "format" && it != "callback" }
            .entries
            .sortedBy { it.key }
            .joinToString(separator = "") { (key, value) -> "$key$value" }
        return Md5.hex((signable + sharedSecret).encodeToByteArray())
    }
}

/**
 * MD5 ([RFC 1321](https://www.rfc-editor.org/rfc/rfc1321)), which Last.fm's signature requires. Written out here
 * because common Kotlin has no digest: `java.security` is JVM-only and CommonCrypto's MD5 is deprecated on iOS.
 */
internal object Md5 {
    private val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    )

    // floor(abs(sin(i + 1)) * 2^32)
    private val constants = IntArray(64) { i ->
        (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
    }

    fun hex(input: ByteArray): String = digest(input).joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    fun digest(input: ByteArray): ByteArray {
        // Pad to 56 mod 64 bytes with 0x80 then zeros, then the message length in bits, little-endian.
        val paddedLength = ((input.size + 8) / 64 + 1) * 64
        val message = input.copyOf(paddedLength)
        message[input.size] = 0x80.toByte()
        val bitLength = input.size.toLong() * 8
        for (i in 0 until 8) message[paddedLength - 8 + i] = (bitLength ushr (8 * i)).toByte()

        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val words = IntArray(16)
        for (chunk in 0 until paddedLength / 64) {
            for (i in 0 until 16) {
                val offset = chunk * 64 + i * 4
                words[i] = (message[offset].toInt() and 0xff) or
                    ((message[offset + 1].toInt() and 0xff) shl 8) or
                    ((message[offset + 2].toInt() and 0xff) shl 16) or
                    ((message[offset + 3].toInt() and 0xff) shl 24)
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
                val rotated = (a + f + constants[i] + words[g]).rotateLeft(shifts[i])
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
        val result = ByteArray(16)
        listOf(a0, b0, c0, d0).forEachIndexed { index, word ->
            for (i in 0 until 4) result[index * 4 + i] = (word ushr (8 * i)).toByte()
        }
        return result
    }
}
