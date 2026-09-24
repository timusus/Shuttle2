package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.fakes.FakePlaylistTimeline
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.toMediaItem
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private const val SAMPLE: Short = 10000
private const val SAMPLE_RATE = 44100
private const val FRAMES_PER_BUFFER = 1024
private const val BUFFERS_PER_TRACK = 3

/**
 * Drives a real [DefaultAudioSink] the way Media3's audio renderers do at a track transition, and
 * checks that every buffer of each track leaves the ReplayGain processor at that track's gain.
 *
 * A FLAC track plays on the libflac renderer (`DecoderAudioRenderer`) and an MP3 on the MediaCodec
 * renderer; both share the one sink. Between two tracks on the same renderer the sink is
 * reconfigured while the first track's audio is still flowing. Between renderers, the first is
 * disabled and reset once its audio has played out and the second is enabled, which flushes the
 * sink, all before the player reports the transition.
 */
@RunWith(RobolectricTestRunner::class)
class ReplayGainTransitionTest {
    private val flac = ReplayGain(trackGain = -6.0, albumGain = -2.0)
    private val mp3 = ReplayGain(trackGain = 4.0, albumGain = 1.0)
    private val timeline =
        FakePlaylistTimeline(
            listOf(
                QueueEntry(uid = 1, song = testSong(id = 1, path = "/music/a.flac", mimeType = MimeTypes.AUDIO_FLAC), replayGain = flac).toMediaItem(),
                QueueEntry(uid = 2, song = testSong(id = 2, path = "/music/b.mp3", mimeType = MimeTypes.AUDIO_MPEG), replayGain = mp3).toMediaItem(),
                QueueEntry(uid = 3, song = testSong(id = 3, path = "/music/c.flac", mimeType = MimeTypes.AUDIO_FLAC), replayGain = flac).toMediaItem()
            )
        )

    private val replayGain = ReplayGainAudioProcessor(ReplayGainMode.Track)
    private val recorder = FirstSampleRecorder()
    private val sink =
        DefaultAudioSink.Builder(RuntimeEnvironment.getApplication())
            .setAudioProcessors(arrayOf(replayGain, recorder))
            .build()

    private var presentationTimeUs = 0L

    @Test
    fun `a gapless transition on the same renderer switches gain at the next track's first buffer`() {
        startTrack(0)
        playBuffers()

        // The renderer reads on into the next item and reconfigures the sink mid-stream.
        startTrack(1)
        playBuffers()

        recorder.firstSamples shouldBe buffersAt(-6.0) + buffersAt(4.0)
    }

    @Test
    fun `a libflac to MediaCodec transition applies the new track's gain from its first buffer`() {
        startTrack(0)
        playBuffers()

        // libflac plays out and is disabled (reset); the MediaCodec renderer is enabled (flush).
        sink.playToEndOfStream()
        sink.reset()
        sink.flush()
        startTrack(1)
        playBuffers()

        recorder.firstSamples shouldBe buffersAt(-6.0) + buffersAt(4.0)
    }

    @Test
    fun `a MediaCodec to libflac transition applies the new track's gain from its first buffer`() {
        startTrack(1)
        playBuffers()

        // MediaCodec plays out and is disabled (flush) then reset; libflac is enabled (flush).
        sink.playToEndOfStream()
        sink.flush()
        sink.reset()
        sink.flush()
        startTrack(2)
        playBuffers()

        recorder.firstSamples shouldBe buffersAt(4.0) + buffersAt(-6.0)
    }

    @Test
    fun `a seek within a track keeps its gain`() {
        startTrack(0)
        playBuffers()
        startTrack(1)
        playBuffers()

        sink.flush()
        playBuffers()

        recorder.firstSamples shouldBe buffersAt(-6.0) + buffersAt(4.0) + buffersAt(4.0)
    }

    @Test
    fun `album mode and the pre-amp apply per track`() {
        replayGain.mode = ReplayGainMode.Album
        replayGain.preAmpGain = 1.5
        startTrack(0)
        playBuffers()
        startTrack(1)
        playBuffers()

        recorder.firstSamples shouldBe buffersAt(-0.5) + buffersAt(2.5)
    }

    /** What the renderer does when its first output for the item at [index] is ready. */
    private fun startTrack(index: Int) {
        sink.configure(
            AudioSink.AudioSinkConfig.Builder(pcm16Stereo)
                .setTimeline(timeline)
                .setMediaPeriodId(MediaPeriodId(timeline.periodUid(index)))
                .build()
        )
    }

    private fun playBuffers() {
        repeat(BUFFERS_PER_TRACK) {
            val buffer = ByteBuffer.allocateDirect(FRAMES_PER_BUFFER * 4).order(ByteOrder.nativeOrder())
            repeat(FRAMES_PER_BUFFER * 2) { buffer.putShort(SAMPLE) }
            buffer.flip()
            var handled = false
            repeat(10) {
                if (!handled) handled = sink.handleBuffer(buffer, presentationTimeUs, 1)
            }
            handled shouldBe true
            presentationTimeUs += FRAMES_PER_BUFFER * C.MICROS_PER_SECOND / SAMPLE_RATE
        }
    }

    private fun buffersAt(gainDb: Double): List<Short> = List(BUFFERS_PER_TRACK) { (SAMPLE * 10.0.pow(gainDb / 20)).toInt().toShort() }

    private val pcm16Stereo =
        Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setChannelCount(2)
            .setSampleRate(SAMPLE_RATE)
            .build()

    /** Passes audio through, noting the first sample of each buffer that reaches it. */
    private class FirstSampleRecorder : BaseAudioProcessor() {
        val firstSamples = mutableListOf<Short>()

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat = inputAudioFormat

        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return
            firstSamples += inputBuffer.getShort(inputBuffer.position())
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }
}
