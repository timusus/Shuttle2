package com.simplecityapps.shuttle.debug.crossfadetap

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun wav(
        sampleRate: Int,
        channels: Int,
        bits: Int,
        pcm: ByteArray
    ): ByteBuffer {
        val file = File(folder.root, "out.wav")
        WavWriter(file, sampleRate, channels, bits).use { it.write(ByteBuffer.wrap(pcm)) }
        return ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    }

    @Test
    fun `header describes 16-bit stereo PCM`() {
        val pcm = ByteArray(400) { it.toByte() }
        val bytes = wav(44_100, 2, 16, pcm)

        assertEquals(44 + 400, bytes.capacity())
        assertEquals("RIFF", String(bytes.array(), 0, 4))
        assertEquals(36 + 400, bytes.getInt(4))
        assertEquals("WAVEfmt ", String(bytes.array(), 8, 8))
        assertEquals(16, bytes.getInt(16))
        assertEquals(1, bytes.getShort(20).toInt())
        assertEquals(2, bytes.getShort(22).toInt())
        assertEquals(44_100, bytes.getInt(24))
        assertEquals(44_100 * 4, bytes.getInt(28))
        assertEquals(4, bytes.getShort(32).toInt())
        assertEquals(16, bytes.getShort(34).toInt())
        assertEquals("data", String(bytes.array(), 36, 4))
        assertEquals(400, bytes.getInt(40))
    }

    @Test
    fun `header describes 24-bit mono PCM and samples follow it unchanged`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        val bytes = wav(48_000, 1, 24, pcm)

        assertEquals(48_000 * 3, bytes.getInt(28))
        assertEquals(3, bytes.getShort(32).toInt())
        assertEquals(24, bytes.getShort(34).toInt())
        assertEquals(pcm.toList(), bytes.array().drop(44))
    }

    @Test
    fun `empty recording is a valid header with no data`() {
        val bytes = wav(44_100, 2, 16, ByteArray(0))

        assertEquals(44, bytes.capacity())
        assertEquals(36, bytes.getInt(4))
        assertEquals(0, bytes.getInt(40))
    }

    @Test
    fun `writes across the scratch buffer size and counts bytes`() {
        val file = File(folder.root, "big.wav")
        val writer = WavWriter(file, 44_100, 2, 16)
        writer.write(ByteBuffer.wrap(ByteArray(20_000) { (it % 7).toByte() }))
        writer.close()

        assertEquals(20_000L, writer.dataBytes)
        assertEquals(44L + 20_000, file.length())
        assertEquals(20_000, ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).getInt(40))
    }

    @Test
    fun `rejects unsupported bit depths`() {
        assertThrows(IllegalArgumentException::class.java) { WavWriter(File(folder.root, "x.wav"), 44_100, 2, 32) }
    }
}
