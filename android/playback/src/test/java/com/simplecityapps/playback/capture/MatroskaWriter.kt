package com.simplecityapps.playback.capture

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Writes 16 bit PCM as a minimal Matroska file (`A_PCM/INT/LIT`, one block per 100 ms cluster) with no cues or seek
 * head, so the extractor finds no way to seek in it.
 */
internal object MatroskaWriter {
    fun write(
        sampleRate: Int,
        channelCount: Int,
        frameCount: Int,
        pcm: ByteArray
    ): ByteArray {
        val bytesPerFrame = channelCount * 2
        val framesPerBlock = sampleRate / 10
        val clusters =
            (0 until frameCount step framesPerBlock).map { from ->
                val to = minOf(from + framesPerBlock, frameCount)
                // Track 1, timecode 0 relative to the cluster's, a keyframe.
                val block = byteArrayOf(0x81.toByte(), 0, 0, 0x80.toByte()) + pcm.copyOfRange(from * bytesPerFrame, to * bytesPerFrame)
                element(0x1F43B675, uint(0xE7, from * 1000L / sampleRate) + element(0xA3, block))
            }
        val header =
            element(
                0x1A45DFA3,
                uint(0x4286, 1) + uint(0x42F7, 1) + uint(0x42F2, 4) + uint(0x42F3, 8) + element(0x4282, "matroska".toByteArray()) + uint(0x4287, 4) + uint(0x4285, 2)
            )
        // Timecodes in milliseconds.
        val info = element(0x1549A966, uint(0x2AD7B1, 1_000_000) + float(0x4489, frameCount * 1000.0 / sampleRate))
        val audio = element(0xE1, float(0xB5, sampleRate.toDouble()) + uint(0x9F, channelCount.toLong()) + uint(0x6264, 16))
        val track = element(0xAE, uint(0xD7, 1) + uint(0x73C5, 1) + uint(0x83, 2) + element(0x86, "A_PCM/INT/LIT".toByteArray()) + audio)
        val segment = element(0x18538067, info + element(0x1654AE6B, track) + clusters.fold(ByteArray(0), ByteArray::plus))
        return header + segment
    }

    /** An element: its ID (which carries its own length marker), its size as an 8 byte EBML integer, then [data]. */
    private fun element(
        id: Int,
        data: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val idBytes = ByteBuffer.allocate(4).putInt(id).array().dropWhile { it == 0.toByte() }
        idBytes.forEach { out.write(it.toInt()) }
        out.write(0x01)
        for (shift in 48 downTo 0 step 8) out.write((data.size.toLong() shr shift).toInt() and 0xFF)
        out.write(data)
        return out.toByteArray()
    }

    private fun uint(
        id: Int,
        value: Long
    ) = element(id, ByteBuffer.allocate(8).putLong(value).array())

    private fun float(
        id: Int,
        value: Double
    ) = element(id, ByteBuffer.allocate(8).putDouble(value).array())
}
