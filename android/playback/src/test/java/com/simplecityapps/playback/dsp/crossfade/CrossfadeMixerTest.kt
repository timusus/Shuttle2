package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.simplecityapps.playback.fakes.FakePlaylistTimeline
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.fakes.withFakeUriStatics
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.toMediaItem
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test

private const val SAMPLE_RATE = 44_100

/** The mixer on its own, driven as the sink's pipeline drives it. The mixing itself is covered by `CrossfadeTest`. */
class CrossfadeMixerTest {
    private val timeline =
        withFakeUriStatics {
            FakePlaylistTimeline(
                listOf(
                    QueueEntry(uid = 1, song = testSong(id = 1, path = "/music/one.wav")).toMediaItem(),
                    QueueEntry(uid = 2, song = testSong(id = 2, path = "/music/two.wav")).toMediaItem()
                )
            )
        }

    private val mixer =
        CrossfadeMixer().apply {
            configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT))
            flush(streamOf(0))
        }

    @Test
    fun `with nothing to mix, each buffer passes through as it is, consumed once the next stage takes it`() {
        val input = pcm(frames = 1_000)
        val bytes = input.contents()

        mixer.queueInput(input)
        val output = mixer.getOutput()

        output shouldBeSameInstanceAs input
        output.contents() shouldBe bytes
        // The pipeline doesn't queue this stage again until its output is taken, which is what consumes the input.
        output.position(output.limit())
        mixer.queueInput(input)
        mixer.getOutput().hasRemaining() shouldBe false
    }

    @Test
    fun `a plan for another entry leaves this stream passing through`() {
        mixer.plans = mapOf(2L to CrossfadePlan(tail(uid = 2, clipEndFrame = 500), CrossfadePlan.Next.FadeOut))
        val input = pcm(frames = 1_000)

        mixer.queueInput(input)

        mixer.getOutput() shouldBeSameInstanceAs input
    }

    @Test
    fun `a plan for this stream still cuts it at the clip end`() {
        mixer.plans = mapOf(1L to CrossfadePlan(tail(uid = 1, clipEndFrame = 441), CrossfadePlan.Next.FadeOut))
        val input = pcm(frames = 1_000)
        val bytes = input.contents()

        mixer.queueInput(input)
        val output = mixer.getOutput()

        input.hasRemaining() shouldBe false
        output.contents() shouldBe bytes.copyOfRange(0, 441 * 4)
    }

    @Test
    fun `a stream that passed through ends once its output is taken`() {
        mixer.queueInput(pcm(frames = 1_000))
        mixer.queueEndOfStream()
        mixer.isEnded() shouldBe false

        mixer.getOutput()

        mixer.isEnded().shouldBeTrue()
    }

    private fun streamOf(index: Int): AudioProcessor.StreamMetadata = AudioProcessor.StreamMetadata.Builder()
        .setTimeline(timeline)
        .setPeriodUid(timeline.periodUid(index))
        .build()

    /** [frames] of distinct stereo 16 bit samples. */
    private fun pcm(frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames * 2) { buffer.putShort((it * 7 - 20_000).toShort()) }
        buffer.flip()
        return buffer
    }

    /** A silent tail of 1 000 frames from the start; [clipEndFrame] a multiple of 441, so it's a whole number of µs. */
    private fun tail(
        uid: Long,
        clipEndFrame: Long
    ) = Tail(
        entryUid = uid,
        sampleRate = SAMPLE_RATE,
        channelCount = 2,
        startUs = 0,
        clipEndUs = clipEndFrame * 1_000_000 / SAMPLE_RATE,
        samples = FloatArray(2_000),
        decodeNanos = 1
    )

    private fun ByteBuffer.contents(): ByteArray = ByteArray(remaining()).also { duplicate().get(it) }
}
