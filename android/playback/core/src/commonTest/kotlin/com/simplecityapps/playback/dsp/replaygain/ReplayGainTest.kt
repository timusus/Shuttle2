package com.simplecityapps.playback.dsp.replaygain

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Pins [replayGainDb], the pure rule shared with iOS (#602): mode selection, tag fallback and the pre-amp. */
class ReplayGainTest {
    private val gain = ReplayGain(trackGain = -6.0, albumGain = 3.0)

    @Test
    fun `track mode applies the track gain plus the pre-amp`() {
        replayGainDb(ReplayGainMode.Track, preAmpGain = 2.0, gain) shouldBe (-4.0 plusOrMinus 0.001)
    }

    @Test
    fun `album mode applies the album gain plus the pre-amp`() {
        replayGainDb(ReplayGainMode.Album, preAmpGain = 2.0, gain) shouldBe (5.0 plusOrMinus 0.001)
    }

    @Test
    fun `track mode falls back to the album gain when the track tag is missing`() {
        replayGainDb(ReplayGainMode.Track, preAmpGain = 0.0, ReplayGain(trackGain = null, albumGain = -5.0)) shouldBe (-5.0 plusOrMinus 0.001)
    }

    @Test
    fun `album mode falls back to the track gain when the album tag is missing`() {
        replayGainDb(ReplayGainMode.Album, preAmpGain = 0.0, ReplayGain(trackGain = 4.0, albumGain = null)) shouldBe (4.0 plusOrMinus 0.001)
    }

    @Test
    fun `a mode with neither tag present applies only the pre-amp`() {
        replayGainDb(ReplayGainMode.Track, preAmpGain = 1.5, ReplayGain(trackGain = null, albumGain = null)) shouldBe (1.5 plusOrMinus 0.001)
        replayGainDb(ReplayGainMode.Track, preAmpGain = 1.5, replayGain = null) shouldBe (1.5 plusOrMinus 0.001)
    }

    @Test
    fun `off mode ignores both tags but still applies the pre-amp`() {
        replayGainDb(ReplayGainMode.Off, preAmpGain = -3.0, gain) shouldBe (-3.0 plusOrMinus 0.001)
        replayGainDb(ReplayGainMode.Off, preAmpGain = 0.0, gain) shouldBe (0.0 plusOrMinus 0.001)
    }
}
