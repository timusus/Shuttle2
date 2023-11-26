package com.simplecityapps.playback.exoplayer

import java.nio.ByteBuffer

object ByteUtils {
    fun ByteBuffer.getInt24(): Int {
        val sample = this.getInt(position() + 2) shl 16 or (this.getInt(position() + 1) and 0xFF shl 8) or (this.getInt(position()) and 0xFF)
        position(position() + 3)
        return sample
    }

    fun ByteBuffer.putInt24(sample: Int): ByteBuffer {
        putInt(sample and 0xFF)
        putInt(sample ushr 8 and 0xFF)
        putInt(sample shr 16)
        return this
    }

    const val Int24_MIN_VALUE = -8388608
    const val Int24_MAX_VALUE = 8388607
}
