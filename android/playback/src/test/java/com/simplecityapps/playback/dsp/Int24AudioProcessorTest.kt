package com.simplecityapps.playback.dsp

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Test

private const val CHANNEL_COUNT = 2
private const val SAMPLE_RATE = 44100
private const val FRAME_COUNT = 1000

/**
 * 24-bit PCM samples are 3 bytes wide, but [com.simplecityapps.playback.exoplayer.ByteUtils.putInt24]
 * used to write 4 bytes per sample, overrunning the output buffer (which `replaceOutputBuffer`
 * sizes to the 3-byte-per-sample input). These tests pin the fix at the processor level.
 */
class Int24AudioProcessorTest {
    @Test
    fun `equalizer with flat preset does not overrun the output buffer for 24 bit PCM`() {
        val processor =
            EqualizerAudioProcessor(enabled = true).apply {
                preset = Equalizer.Presets.flat
            }
        processor.configure(AudioProcessor.AudioFormat(SAMPLE_RATE, CHANNEL_COUNT, C.ENCODING_PCM_24BIT))
        processor.flush()

        val input = random24BitPcm()
        processor.queueInput(input.duplicate())
        val output = processor.getOutput()

        output.remaining() shouldBe input.remaining()
    }

    @Test
    fun `replay gain does not overrun the output buffer for 24 bit PCM`() {
        val processor = ReplayGainAudioProcessor(mode = ReplayGainMode.Track, preAmpGain = 6.0)
        processor.streamTracker.setPlaylist(listOf(ReplayGain(trackGain = -3.0, albumGain = null)))
        processor.configure(AudioProcessor.AudioFormat(SAMPLE_RATE, CHANNEL_COUNT, C.ENCODING_PCM_24BIT))
        processor.flush()

        val input = random24BitPcm()
        processor.queueInput(input.duplicate())
        val output = processor.getOutput()

        output.remaining() shouldBe input.remaining()
    }

    /** [FRAME_COUNT] stereo frames of random, non-clipping 24-bit PCM, little-endian as ExoPlayer produces it. */
    private fun random24BitPcm(): ByteBuffer {
        val random = Random(seed = 20260923)
        val buffer = ByteBuffer.allocateDirect(FRAME_COUNT * CHANNEL_COUNT * 3).order(ByteOrder.LITTLE_ENDIAN)
        repeat(FRAME_COUNT * CHANNEL_COUNT) {
            buffer.putInt24(random.nextInt(-Int24_MAX_VALUE, Int24_MAX_VALUE))
        }
        buffer.flip()
        return buffer
    }
}
