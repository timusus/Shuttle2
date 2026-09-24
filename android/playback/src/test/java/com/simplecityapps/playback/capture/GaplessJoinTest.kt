package com.simplecityapps.playback.capture

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.spec.PlaybackHarness
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAMPLE_RATE = 44_100

/** One 16 bit quantisation step. */
private const val LSB = 1.0 / 32768

/**
 * An album's songs play back to back with nothing inserted, dropped or disturbed at each join: one continuous tone,
 * cut into three files at frames that don't line up with any buffer, comes out as the same tone played as one file.
 */
@RunWith(RobolectricTestRunner::class)
class GaplessJoinTest {
    /** A 440 Hz tone cut into three songs of uneven length; the phase runs on across each cut. */
    private val tone = Wav.tones(440.0 to 0.5, frames = 3 * SAMPLE_RATE, sampleRate = SAMPLE_RATE)
    private val cuts = listOf(0, 40_001, 91_333, tone.frameCount)
    private val joins = cuts.drop(1).dropLast(1)
    private val tracks = cuts.zipWithNext { from, to -> tone.slice(from, to) }

    @Test
    fun `three songs with the chain passing audio through come out as the original tone, sample for sample`() {
        val output = PlaybackHarness().use { it.playToEnd(tracks.mapIndexed { index, track -> track.song(id = index + 1L) }) }

        output.frameCount shouldBe tone.frameCount
        // Past the sink's fade-in, every sample is the source's.
        val expected = tone.int16Samples()
        val actual = output.channel()
        val mismatches = (output.fadeInFrames until tone.frameCount).filter { expected[it] != actual[it] }
        mismatches.take(5).map { "frame $it: ${actual[it]} not ${expected[it]}" } shouldBe emptyList()
    }

    @Test
    fun `with the same album gain on every song, the joins come out as if the tone were one file`() {
        shouldPlayAsOneFile(replayGainAlbum = -4.5) { PlaybackHarness(replayGainMode = ReplayGainMode.Album) }
    }

    @Ignore("#365")
    @Test
    fun `with the equalizer on, the joins come out as if the tone were one file`() {
        shouldPlayAsOneFile {
            PlaybackHarness(equalizerEnabled = true).apply { equalizer.preset = Equalizer.Presets.bassBoost }
        }
    }

    /**
     * Plays [tracks], each tagged [replayGainAlbum], and the whole [tone] as one file, each on a harness from [harness].
     * After the sink's fade-in the two agree to within a quantisation step, there's no silence (not even two zero
     * samples in a row, which a 440 Hz tone never has), and around each join (±64 frames) no step between neighbouring
     * samples is bigger than the largest step inside a song. A gap, a dropped buffer or a filter restart shows up in
     * all three.
     */
    private fun shouldPlayAsOneFile(
        replayGainAlbum: Double? = null,
        harness: () -> PlaybackHarness
    ) {
        val gapless = harness().use { it.playToEnd(tracks.mapIndexed { index, track -> track.song(id = index + 1L, replayGainAlbum = replayGainAlbum) }) }
        val oneFile = harness().use { it.playToEnd(listOf(tone.song(id = 1, replayGainAlbum = replayGainAlbum))) }

        gapless.frameCount shouldBe tone.frameCount
        oneFile.frameCount shouldBe tone.frameCount
        val played = gapless.channel(from = gapless.fadeInFrames)
        val expected = oneFile.channel(from = oneFile.fadeInFrames)
        played.indices.maxOf { abs(played[it] - expected[it]) } shouldBeLessThan 1.5 * LSB
        played.longestSilence() shouldBeLessThan 2
        // The steady state, away from the start (filters settling) and the joins.
        val inTrackStep = gapless.channel(from = 10_000, to = 40_000).maxStep()
        joins.forEach { join ->
            gapless.channel(from = join - 64, to = join + 64).maxStep() shouldBeLessThan inTrackStep + LSB
        }
    }
}
