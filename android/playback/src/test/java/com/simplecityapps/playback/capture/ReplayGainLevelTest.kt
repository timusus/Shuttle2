package com.simplecityapps.playback.capture

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.spec.PlaybackHarness
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.sign
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAMPLE_RATE = 44_100

/**
 * A song tagged with ReplayGain comes out louder or quieter than its source by the tag's gain plus the pre-amp, in
 * each mode: measured as the output's RMS level against the source's, on a 1 kHz tone at -12 dBFS (peak).
 */
// Robolectric: PlaybackHarness spec test.
@RunWith(RobolectricTestRunner::class)
class ReplayGainLevelTest {
    private val tone = Wav.tones(1_000.0 to 0.25, frames = SAMPLE_RATE / 2, sampleRate = SAMPLE_RATE)

    @Test
    fun `track mode applies the track gain plus the pre-amp`() {
        levelChange(ReplayGainMode.Track, preAmp = 2.0, trackGain = -6.0, albumGain = 3.0) shouldBe (-4.0 plusOrMinus 0.5)
    }

    @Test
    fun `album mode applies the album gain plus the pre-amp`() {
        levelChange(ReplayGainMode.Album, preAmp = 2.0, trackGain = -6.0, albumGain = 3.0) shouldBe (5.0 plusOrMinus 0.5)
    }

    @Test
    fun `each mode falls back to the other tag when its own is missing`() {
        levelChange(ReplayGainMode.Track, preAmp = 0.0, trackGain = null, albumGain = -5.0) shouldBe (-5.0 plusOrMinus 0.5)
        levelChange(ReplayGainMode.Album, preAmp = 0.0, trackGain = 4.0, albumGain = null) shouldBe (4.0 plusOrMinus 0.5)
    }

    @Test
    fun `with ReplayGain off only the pre-amp applies`() {
        levelChange(ReplayGainMode.Off, preAmp = -3.0, trackGain = -6.0, albumGain = 3.0) shouldBe (-3.0 plusOrMinus 0.5)
        levelChange(ReplayGainMode.Off, preAmp = 0.0, trackGain = -6.0, albumGain = 3.0) shouldBe (0.0 plusOrMinus 0.01)
    }

    /**
     * There's no clipping prevention beyond a hard limit: a gain that takes a -6 dBFS tone past full scale flattens its
     * peaks at full scale. The samples saturate rather than wrap round, so every one keeps the source's sign.
     */
    @Test
    fun `a gain past full scale saturates the peaks instead of wrapping round`() {
        val loud = Wav.tones(1_000.0 to 0.5, frames = SAMPLE_RATE / 2, sampleRate = SAMPLE_RATE)

        val output = PlaybackHarness(replayGainMode = ReplayGainMode.Track).use { it.playToEnd(listOf(loud.song(id = 1, replayGainTrack = 12.0))) }

        output.frameCount shouldBe loud.frameCount
        val played = output.channel(from = output.fadeInFrames)
        val source = loud.samples.copyOfRange(output.fadeInFrames, loud.frameCount)
        played.maxOf { abs(it) } shouldBeGreaterThan 0.999
        source.indices.count { source[it].sign * played[it].sign < 0 } shouldBe 0
        // Unclipped, the peaks would reach 0.5 * 10^(12/20), about 2; a wrapped sample steps by nearly 2.
        played.maxStep() shouldBeLessThan 0.5
    }

    /** The output's RMS level relative to the source's, in dB, past the sink's fade-in. */
    private fun levelChange(
        mode: ReplayGainMode,
        preAmp: Double,
        trackGain: Double?,
        albumGain: Double?
    ): Double {
        val output =
            PlaybackHarness(replayGainMode = mode).use { harness ->
                harness.replayGain.preAmpGain = preAmp
                harness.playToEnd(listOf(tone.song(id = 1, replayGainTrack = trackGain, replayGainAlbum = albumGain)))
            }
        output.frameCount shouldBe tone.frameCount
        val from = output.fadeInFrames
        return (output.channel(from = from).rms() / tone.samples.copyOfRange(from, tone.frameCount).rms()).toDb()
    }
}
