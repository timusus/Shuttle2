package com.simplecityapps.shuttle.debug.crossfadetap

import java.io.BufferedOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes little-endian integer PCM ([bitsPerSample] 16 or 24) to a canonical 44-byte-header WAV file. The header goes
 * out with zero sizes first; [close] patches in the real ones.
 */
open class WavWriter(
    private val file: File,
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int
) : AutoCloseable {
    init {
        require(bitsPerSample == 16 || bitsPerSample == 24) { "unsupported bit depth $bitsPerSample" }
    }

    private val out = BufferedOutputStream(file.outputStream())
    private val scratch = ByteArray(8192)
    private var closed = false

    /** The PCM bytes written so far. */
    var dataBytes = 0L
        private set

    init {
        out.write(header(0))
    }

    /** Appends the buffer's remaining bytes (PCM in the writer's format, little-endian) and consumes them. */
    open fun write(buffer: ByteBuffer) {
        check(!closed) { "closed" }
        while (buffer.hasRemaining()) {
            val count = minOf(buffer.remaining(), scratch.size)
            buffer.get(scratch, 0, count)
            out.write(scratch, 0, count)
            dataBytes += count
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        out.close()
        RandomAccessFile(file, "rw").use { it.write(header(dataBytes)) }
    }

    private fun header(dataSize: Long): ByteArray {
        val blockAlign = channelCount * bitsPerSample / 8
        return ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((HEADER_BYTES - 8 + dataSize).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // integer PCM
            putShort(channelCount.toShort())
            putInt(sampleRate)
            putInt(sampleRate * blockAlign)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSize.toInt())
        }.array()
    }

    companion object {
        const val HEADER_BYTES = 44
    }
}
