package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.dsp.replaygain.replayGainDb
import com.simplecityapps.playback.exoplayer.PlayerItem
import com.simplecityapps.playback.exoplayer.toMediaItem
import com.simplecityapps.playback.fakes.FakePlaylistTimeline
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAMPLE: Short = 10000

// Robolectric for android.net.Uri, which MediaItem parses the item uri into.
@RunWith(RobolectricTestRunner::class)
class ReplayGainAudioProcessorTest {
    private val loud = ReplayGain(trackGain = -6.0, albumGain = -4.0)
    private val quiet = ReplayGain(trackGain = 6.0, albumGain = 3.0)
    private val timeline =
        FakePlaylistTimeline(
            listOf(
                PlayerItem(uri = "/music/loud.flac", mimeType = null, replayGain = loud).toMediaItem(),
                PlayerItem(uri = "/music/quiet.mp3", mimeType = null, replayGain = quiet).toMediaItem(),
                PlayerItem(uri = "/music/untagged.mp3", mimeType = null, replayGain = null).toMediaItem()
            )
        )

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
    fun `each stream plays at the gain of the item the sink flushes the processor for`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Track)
        processor.configure(pcm16Stereo)

        processor.flush(streamOf(0))
        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -6.0)

        processor.flush(streamOf(1))
        processor.process(SAMPLE) shouldBe scaled(SAMPLE, 6.0)

        processor.flush(streamOf(2))
        processor.process(SAMPLE) shouldBe SAMPLE
    }

    @Test
    fun `album mode and the pre-amp apply to the stream's item`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Album, preAmpGain = 2.0)
        processor.configure(pcm16Stereo)

        processor.flush(streamOf(1))

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, 5.0)
    }

    @Test
    fun `mode and pre-amp changes apply to the next buffer`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Off)
        processor.configure(pcm16Stereo)
        processor.flush(streamOf(0))
        processor.process(SAMPLE) shouldBe SAMPLE

        processor.mode = ReplayGainMode.Track
        processor.preAmpGain = 2.0

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -4.0)
    }

    @Test
    fun `a flush that doesn't identify an item keeps the stream's gain`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Track)
        processor.configure(pcm16Stereo)
        processor.flush(streamOf(0))

        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, -6.0)
    }

    @Test
    fun `a reset forgets the stream's gain`() {
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Track, preAmpGain = 1.0)
        processor.configure(pcm16Stereo)
        processor.flush(streamOf(0))

        processor.reset()
        processor.configure(pcm16Stereo)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        processor.process(SAMPLE) shouldBe scaled(SAMPLE, 1.0)
    }

    private val pcm16Stereo = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

    private fun streamOf(index: Int): AudioProcessor.StreamMetadata = AudioProcessor.StreamMetadata.Builder()
        .setTimeline(timeline)
        .setPeriodUid(timeline.periodUid(index))
        .build()

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
