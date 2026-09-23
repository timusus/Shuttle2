package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.dsp.replaygain.replayGainDb
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import org.junit.Test

private const val SAMPLE: Short = 10000

class ReplayGainAudioProcessorTest {
    @Test
    fun `track mode prefers the track gain, falling back to the album gain`() {
        replayGainDb(ReplayGainMode.Track, 0.0, ReplayGain(trackGain = -3.0, albumGain = -6.0)) shouldBe -3.0
        replayGainDb(ReplayGainMode.Track, 0.0, ReplayGain(trackGain = null, albumGain = -6.0)) shouldBe -6.0
    }

    @Test
    fun `album mode prefers the album gain, falling back to the track gain`() {
        replayGainDb(ReplayGainMode.Album, 0.0, ReplayGain(trackGain = -3.0, albumGain = -6.0)) shouldBe -6.0
        replayGainDb(ReplayGainMode.Album, 0.0, ReplayGain(trackGain = -3.0, albumGain = null)) shouldBe -3.0
    }

    @Test
    fun `the pre-amp applies on top of the tag, and alone when there is no tag or the mode is off`() {
        replayGainDb(ReplayGainMode.Track, -4.0, ReplayGain(trackGain = -1.5, albumGain = null)) shouldBe -5.5
        replayGainDb(ReplayGainMode.Track, -4.0, null) shouldBe -4.0
        replayGainDb(ReplayGainMode.Off, -4.0, ReplayGain(trackGain = -1.5, albumGain = -7.0)) shouldBe -4.0
    }

    @Test
    fun `the next track's gain applies from its first buffer, not when the player reports the transition`() {
        val songA = ReplayGain(trackGain = -6.0, albumGain = null)
        val songB = ReplayGain(trackGain = 6.0, albumGain = null)
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Track, preAmpGain = 0.0)
        val tracker = processor.streamTracker
        tracker.setPlaylist(listOf(songA, songB))
        tracker.setPlayingIndex(0)

        // Load: the sink resets, configures and flushes the processors before the first buffer.
        tracker.onSinkRestarted()
        tracker.onSinkConfigured()
        processor.configure(pcm16Stereo)
        tracker.onSinkBufferHandled()
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -6.0)

        // The renderer reaches song B: the sink reconfigures, drains A, then flushes before B.
        tracker.onSinkConfigured()
        processor.configure(pcm16Stereo)
        tracker.onSinkBufferHandled()
        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -6.0)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, 6.0)
    }

    @Test
    fun `mode and pre-amp changes apply to the next buffer`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Off, preAmpGain = 0.0)
        processor.streamTracker.setPlaylist(listOf(ReplayGain(trackGain = -6.0, albumGain = null)))
        processor.configure(pcm16Stereo)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.process(SAMPLE) shouldBe SAMPLE

        processor.mode = ReplayGainMode.Track
        processor.preAmpGain = 2.0

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -4.0)
    }

    private val pcm16Stereo = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

    private fun scaled(
        sample: Short,
        gainDb: Double
    ): Short = (sample * 10.0.pow(gainDb / 20)).toInt().toShort()

    /** Queues one stereo frame of [sample] and returns the processed left channel. */
    private fun ReplayGainAudioProcessor.process(sample: Short): Short {
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(sample).putShort(sample).flip()
        queueInput(input)
        return getOutput().short
    }
}
