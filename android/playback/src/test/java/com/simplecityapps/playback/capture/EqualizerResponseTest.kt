package com.simplecityapps.playback.capture

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SAMPLE_RATE = 44_100

/** The 1 kHz band's centre, and a tone well outside its 350 Hz bandwidth that no other band touches at 0 dB. */
private const val BAND = 1_000.0
private const val OFF_BAND = 5_000.0

/** 100 cycles of [BAND] and 500 of [OFF_BAND], so a single-bin DFT over whole windows measures each exactly. */
private const val WINDOW = SAMPLE_RATE / 10

/**
 * The equalizer changes the level of the frequencies its bands cover by their gain, measured on the captured output
 * with a single-bin DFT at the band's centre and at a frequency outside it.
 */
@RunWith(RobolectricTestRunner::class)
class EqualizerResponseTest {
    private val custom = Equalizer.Presets.custom
    private val band = custom.bands.single { it.centerFrequency == BAND.toInt() }

    private val tones = Wav.tones(BAND to 0.2, OFF_BAND to 0.2, frames = SAMPLE_RATE, sampleRate = SAMPLE_RATE)

    @After
    fun tearDown() {
        // The custom preset is app-wide; leave it flat for the next test.
        band.gain = 0.0
    }

    @Test
    fun `a band cut lowers its frequency by the cut and leaves the rest alone`() {
        band.gain = -6.0

        val output = playWithCustomPreset(listOf(tones.song(id = 1))).steadyState(from = 0, frames = tones.frameCount)

        (output.amplitudeAt(BAND, SAMPLE_RATE) / 0.2).toDb() shouldBe (-6.0 plusOrMinus 1.0)
        (output.amplitudeAt(OFF_BAND, SAMPLE_RATE) / 0.2).toDb() shouldBe (0.0 plusOrMinus 0.5)
    }

    /** A boost lowers the whole output by its gain first, to keep headroom (RS-18), so it shows relative to the other bands. */
    @Test
    fun `a band boost raises its frequency by the boost relative to the rest`() {
        band.gain = 6.0

        val output = playWithCustomPreset(listOf(tones.song(id = 1))).steadyState(from = 0, frames = tones.frameCount)

        (output.amplitudeAt(BAND, SAMPLE_RATE) / output.amplitudeAt(OFF_BAND, SAMPLE_RATE)).toDb() shouldBe (6.0 plusOrMinus 1.0)
    }

    @Test
    fun `the equalizer still applies after skipping to the next song`() {
        band.gain = -6.0
        // A different level from the first song, so the measurement can only be of the second.
        val next = Wav.tones(BAND to 0.1, OFF_BAND to 0.1, frames = SAMPLE_RATE, sampleRate = SAMPLE_RATE)

        val captured =
            playWithCustomPreset(listOf(tones.song(id = 1), next.song(id = 2))) { harness ->
                harness.runUntil { (harness.playbackOperations.progressFlow.value?.position ?: 0) >= 300 }
                harness.playbackOperations.skipToNext()
            }
        // The skip flushes the sink, dropping what it had written ahead, so the second song is the last of what's heard.
        val output = captured.steadyState(from = captured.frameCount - next.frameCount, frames = next.frameCount)

        (output.amplitudeAt(BAND, SAMPLE_RATE) / 0.1).toDb() shouldBe (-6.0 plusOrMinus 1.0)
        (output.amplitudeAt(OFF_BAND, SAMPLE_RATE) / 0.1).toDb() shouldBe (0.0 plusOrMinus 0.5)
    }

    /** What [com.simplecityapps.playback.BitPerfectOutput] does while it sends audio to a USB DAC unchanged. */
    @Test
    fun `bypassed, neither the equalizer nor ReplayGain changes a sample`() {
        band.gain = -6.0

        val output =
            PlaybackHarness(replayGainMode = ReplayGainMode.Track, equalizerEnabled = true).use { harness ->
                harness.equalizer.preset = custom
                harness.equalizer.bypassed = true
                harness.replayGain.bypassed = true
                harness.playToEnd(listOf(tones.song(id = 1, replayGainTrack = -8.0)))
            }

        val expected = tones.int16Samples()
        val actual = output.channel()
        (output.fadeInFrames until tones.frameCount).count { expected[it] != actual[it] } shouldBe 0
    }

    private fun playWithCustomPreset(
        songs: List<Song>,
        whilePlaying: (PlaybackHarness) -> Unit = {}
    ): CapturedAudio = PlaybackHarness(equalizerEnabled = true).use { harness ->
        harness.equalizer.preset = custom
        harness.playToEnd(songs) { whilePlaying(harness) }
    }

    /**
     * Whole [WINDOW]s of the song that starts at frame [from] and runs [frames], skipping its first 200 ms, while the
     * sink fades in and the filters settle.
     */
    private fun CapturedAudio.steadyState(
        from: Int,
        frames: Int
    ): DoubleArray {
        val start = from + 2 * WINDOW
        return channel(from = start, to = start + (frames - 2 * WINDOW) / WINDOW * WINDOW)
    }
}
