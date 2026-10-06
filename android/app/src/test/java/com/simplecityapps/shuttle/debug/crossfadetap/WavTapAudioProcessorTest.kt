package com.simplecityapps.shuttle.debug.crossfadetap

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WavTapAudioProcessorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FailingWriter(file: File) : WavWriter(file, 44_100, 2, 16) {
        override fun write(buffer: ByteBuffer) = throw IOException("disk full")
    }

    private fun processor(failing: Boolean = false) = WavTapAudioProcessor(
        if (failing) {
            { file, _, _, _ -> FailingWriter(file) }
        } else {
            ::WavWriter
        }
    ).apply {
        configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    private fun WavTapAudioProcessor.feed(bytes: Int): ByteBuffer {
        queueInput(ByteBuffer.wrap(ByteArray(bytes) { it.toByte() }))
        return output
    }

    @Test
    fun `a failing write drops the recording and the audio passes through`() {
        val tap = processor(failing = true)
        tap.start(File(folder.root, "out.wav"))

        val output = tap.feed(64)

        assertEquals(64, output.remaining())
        assertFalse(tap.isRecording)
        assertNull(tap.stop())
    }

    @Test
    fun `a reset before any audio keeps the recording`() {
        val tap = processor()
        tap.start(File(folder.root, "out.wav"))

        tap.reset()

        assertEquals(true, tap.isRecording)
        tap.feed(64)
        assertEquals(64L, tap.stop()?.dataBytes)
    }

    @Test
    fun `stop after a reset reports the finalised file`() {
        val tap = processor()
        tap.start(File(folder.root, "out.wav"))
        tap.feed(64)

        tap.reset()

        assertFalse(tap.isRecording)
        val result = tap.stop()
        assertNotNull(result)
        assertEquals(64L, result?.dataBytes)
        assertNull(tap.stop())
    }
}
