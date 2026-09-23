package com.simplecityapps.playback.exoplayer

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ByteUtils {
    fun ByteBuffer.getInt24(): Int {
        val pos = position()
        val b0 = get(pos).toInt() and 0xFF
        val b1 = get(pos + 1).toInt() and 0xFF
        val b2 = get(pos + 2).toInt() and 0xFF
        val unsigned = if (order() == ByteOrder.LITTLE_ENDIAN) b0 or (b1 shl 8) or (b2 shl 16) else (b0 shl 16) or (b1 shl 8) or b2
        position(pos + 3)
        // Sign-extend the 24-bit value to a 32-bit Int.
        return (unsigned shl 8) shr 8
    }

    fun ByteBuffer.putInt24(sample: Int): ByteBuffer {
        val clamped = sample.coerceIn(Int24_MIN_VALUE, Int24_MAX_VALUE)
        val pos = position()
        if (order() == ByteOrder.LITTLE_ENDIAN) {
            put(pos, (clamped and 0xFF).toByte())
            put(pos + 1, (clamped ushr 8 and 0xFF).toByte())
            put(pos + 2, (clamped ushr 16 and 0xFF).toByte())
        } else {
            put(pos, (clamped ushr 16 and 0xFF).toByte())
            put(pos + 1, (clamped ushr 8 and 0xFF).toByte())
            put(pos + 2, (clamped and 0xFF).toByte())
        }
        position(pos + 3)
        return this
    }

    const val Int24_MIN_VALUE = -8388608
    const val Int24_MAX_VALUE = 8388607
}
