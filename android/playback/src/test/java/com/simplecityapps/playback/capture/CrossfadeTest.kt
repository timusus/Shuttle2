package com.simplecityapps.playback.capture

import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timber.log.Timber

private const val SAMPLE_RATE = 44_100

private const val CROSSFADE_MS = 2_000

private const val CROSSFADE_FRAMES = SAMPLE_RATE * CROSSFADE_MS / 1000

/** 100 ms: a whole number of cycles of each tone, so [amplitudeAt] reads each exactly. */
private const val WINDOW = SAMPLE_RATE / 10

/** One 16 bit quantisation step. */
private const val LSB = 1.0 / 32768

/**
 * Crossfade (#97, design B): each song is clipped to end [CROSSFADE_MS] early and its decoded tail is mixed into the
 * next song's head. Lengths are whole milliseconds (the songs' durations are), and the clip points fall inside the WAV
 * extractor's 100 ms access units, so the clip overshoots and the mixer has to line the tail up itself.
 */
// Robolectric: PlaybackHarness spec test.
@RunWith(RobolectricTestRunner::class)
class CrossfadeTest {
    private val a = Wav.tones(440.0 to 0.5, frames = 441 * 525, sampleRate = SAMPLE_RATE)
    private val b = Wav.tones(660.0 to 0.5, frames = 441 * 611, sampleRate = SAMPLE_RATE)

    /** Too short to clip (under twice the crossfade), so it plays whole. */
    private val c = Wav.tones(880.0 to 0.5, frames = 441 * 300, sampleRate = SAMPLE_RATE)

    /** Where A's clipped item ends and the overlap starts. */
    private val join = a.frameCount - CROSSFADE_FRAMES

    /** The tail decoder's reports, one per tail: decoded, or why not. */
    private val decodeLog = mutableListOf<String>()

    private val tree =
        object : Timber.Tree() {
            override fun log(
                priority: Int,
                tag: String?,
                message: String,
                t: Throwable?
            ) {
                if (message.startsWith("Crossfade: ")) decodeLog += message
            }
        }

    @Before
    fun plant() = Timber.plant(tree)

    @After
    fun uproot() {
        Timber.uproot(tree)
        decodeLog.forEach(::println)
    }

    @Test
    fun `one song fades into the next over the crossfade, and the last fades out`() {
        val output = PlaybackHarness(crossfadeDurationMs = CROSSFADE_MS).use { it.playWithTails(listOf(a.song(id = 1), b.song(id = 2))) }

        output.frameCount shouldBe a.frameCount + b.frameCount - CROSSFADE_FRAMES
        val samples = output.channel()
        fun amplitudes(
            frequency: Double,
            from: Int,
            to: Int
        ) = (from until to step WINDOW).map { samples.copyOfRange(it, it + WINDOW).amplitudeAt(frequency, SAMPLE_RATE) }

        // Before the overlap only A plays, at full level; after it only B.
        amplitudes(440.0, join - 10 * WINDOW, join).forEach { it shouldBe (0.5 plusOrMinus 0.01) }
        amplitudes(660.0, join - 10 * WINDOW, join).forEach { it shouldBeLessThan 0.001 }
        amplitudes(660.0, join + CROSSFADE_FRAMES, join + CROSSFADE_FRAMES + 10 * WINDOW).forEach { it shouldBe (0.5 plusOrMinus 0.01) }
        amplitudes(440.0, join + CROSSFADE_FRAMES, join + CROSSFADE_FRAMES + 10 * WINDOW).forEach { it shouldBeLessThan 0.001 }

        // Across the overlap both play, A falling as B rises, meeting at -3 dB halfway.
        val falling = amplitudes(440.0, join, join + CROSSFADE_FRAMES)
        val rising = amplitudes(660.0, join, join + CROSSFADE_FRAMES)
        falling.zipWithNext().forEach { (earlier, later) -> later shouldBeLessThan earlier }
        rising.zipWithNext().forEach { (earlier, later) -> later shouldBeGreaterThan earlier }
        falling.first() shouldBeGreaterThan 0.49
        rising.last() shouldBeGreaterThan 0.49
        val middle = falling.size / 2
        falling[middle] shouldBe (0.5 * 0.707 plusOrMinus 0.02)
        rising[middle] shouldBe (0.5 * 0.707 plusOrMinus 0.02)

        // No gap, and no step at either end of the overlap bigger than the tones make on their own.
        samples.copyOfRange(output.fadeInFrames, samples.size - 64).longestSilence() shouldBeLessThan 2
        val toneStep = maxOf(samples.copyOfRange(10_000, 40_000).maxStep(), samples.copyOfRange(join + CROSSFADE_FRAMES + 10_000, join + CROSSFADE_FRAMES + 40_000).maxStep())
        samples.copyOfRange(join - 64, join + 64).maxStep() shouldBeLessThan toneStep + LSB
        samples.copyOfRange(join + CROSSFADE_FRAMES - 64, join + CROSSFADE_FRAMES + 64).maxStep() shouldBeLessThan toneStep + LSB

        // B's tail plays out, fading, as the queue ends.
        val fadingOut = amplitudes(660.0, samples.size - CROSSFADE_FRAMES, samples.size)
        fadingOut.zipWithNext().forEach { (earlier, later) -> later shouldBeLessThan earlier }
        fadingOut.last() shouldBeLessThan 0.05
    }

    @Test
    fun `a skip during the overlap cuts straight to the next song`() {
        val output =
            PlaybackHarness(crossfadeDurationMs = CROSSFADE_MS).use { harness ->
                harness.playWithTails(listOf(a.song(id = 1), b.song(id = 2), c.song(id = 3))) {
                    harness.runAt(mediaItemIndex = 1, positionMs = CROSSFADE_MS / 10L) {
                        harness.appPlayer.currentMediaItemIndex shouldBe 1
                        harness.appPlayer.currentPosition shouldBeLessThan CROSSFADE_MS * 3 / 4L
                        // The sink here has written ahead to the end of the queue: what follows the skip is what it
                        // writes from the skip's flush on.
                        harness.clearAudioOutput()
                        harness.playbackOperations.skipToNext()
                    }
                }
            }

        // C plays whole, from its start, with nothing of A's tail or B mixed in.
        output.frameCount shouldBe c.frameCount
        val expected = c.int16Samples()
        val actual = output.channel()
        (output.fadeInFrames until expected.size).filter { expected[it] != actual[it] }.take(5) shouldBe emptyList()
    }

    @Test
    fun `a clipped song shows its whole duration to the session`() {
        val lengthMs = a.frameCount * 1000L / SAMPLE_RATE
        PlaybackHarness(crossfadeDurationMs = CROSSFADE_MS).use { harness ->
            harness.playWithTails(listOf(a.song(id = 1), b.song(id = 2))) {
                harness.runAt(mediaItemIndex = 0, positionMs = 1_000) {
                    harness.appPlayer.duration shouldBe lengthMs
                    harness.playbackOperations.getDuration() shouldBe lengthMs.toInt()
                }
            }
        }
    }

    @Test
    fun `with crossfade off, songs play back to back unchanged`() {
        val output = PlaybackHarness(crossfadeDurationMs = 0).use { it.playToEnd(listOf(a.song(id = 1), b.song(id = 2))) }

        output.frameCount shouldBe a.frameCount + b.frameCount
        val expected = a.int16Samples() + b.int16Samples()
        val actual = output.channel()
        (output.fadeInFrames until expected.size).filter { expected[it] != actual[it] }.take(5) shouldBe emptyList()
    }

    @Test
    fun `songs of one album join sample for sample, their clipped tails played out unfaded`() {
        // One tone cut into an album's songs: two long enough to clip, at points inside the extractor's access units,
        // then one too short to.
        val tone = Wav.tones(440.0 to 0.5, frames = 220_501 + 220_832 + 3 * SAMPLE_RATE, sampleRate = SAMPLE_RATE)
        val cuts = listOf(0, 220_501, 441_333, tone.frameCount)
        val songs = cuts.zipWithNext { from, to -> tone.slice(from, to) }.mapIndexed { index, track -> track.song(id = index + 1L, album = "Album") }

        val output = PlaybackHarness(crossfadeDurationMs = CROSSFADE_MS).use { it.playWithTails(songs) }

        output.frameCount shouldBe tone.frameCount
        val expected = tone.int16Samples()
        val actual = output.channel()
        (output.fadeInFrames until tone.frameCount).filter { expected[it] != actual[it] }.take(5) shouldBe emptyList()
    }

    @Test
    fun `a song whose tail can't be decoded plays whole`() {
        // A stream the decoder can't seek in, so it has no tail, then a song that has one.
        val output = PlaybackHarness(crossfadeDurationMs = CROSSFADE_MS).use { it.playWithTails(listOf(a.unseekableSong(id = 1), b.song(id = 2))) }

        decodeLog.first() shouldContain "can't seek"
        // A plays to its end, then B, with no crossfade between them (B's own tail still fades it out).
        output.frameCount shouldBe a.frameCount + b.frameCount
        val expected = a.int16Samples() + b.int16Samples().copyOfRange(0, b.frameCount - CROSSFADE_FRAMES)
        val actual = output.channel()
        (output.fadeInFrames until expected.size).filter { expected[it] != actual[it] }.take(5) shouldBe emptyList()
    }

    /**
     * [playToEnd], but queueing the songs first and playing only once the first two songs' tails are decoded. The
     * decoder works in wall time, on a real loader thread, while the sink here takes a whole song at once, so without
     * this the songs would have gone through the mixer before their tails were ready. On a device the decode runs many
     * times faster than playback; it only has to finish before the sink reaches the song's end.
     */
    private fun PlaybackHarness.playWithTails(
        songs: List<Song>,
        whilePlaying: () -> Unit = {}
    ): CapturedAudio {
        val ended = record(playbackOperations.trackEndedFlow)
        run { queueOperations.setQueue(songs) }
        runUntil { decodeLog.size >= 2 }
        playbackOperations.play()
        whilePlaying()
        runUntil { ended.lastOrNull() == songs.last() }
        return capturedAudio()
    }
}
