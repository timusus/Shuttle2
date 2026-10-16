package com.simplecityapps.playback.dsp.replaygain

import com.simplecityapps.playback.queue.song
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

    private fun movement(id: Long, albumGain: Double? = 3.0) = song(id).copy(
        work = "Symphony No. 5",
        composer = "Beethoven",
        genres = listOf("Classical"),
        replayGainTrack = -6.0,
        replayGainAlbum = albumGain
    )

    private fun lone(id: Long) = song(id).copy(replayGainTrack = -6.0, replayGainAlbum = 3.0)

    @Test
    fun `track mode uses album gain for a movement of a work`() {
        val queue = listOf(lone(1), movement(2), movement(3), movement(4), lone(5))
        (1..3).forEach { replayGainModeAt(ReplayGainMode.Track, queue, it) shouldBe ReplayGainMode.Album }
    }

    @Test
    fun `track mode keeps track gain for songs outside a work`() {
        val queue = listOf(lone(1), movement(2), lone(3), lone(4))
        replayGainModeAt(ReplayGainMode.Track, queue, 0) shouldBe ReplayGainMode.Track
        replayGainModeAt(ReplayGainMode.Track, queue, 1) shouldBe ReplayGainMode.Track
        replayGainModeAt(ReplayGainMode.Track, queue, 3) shouldBe ReplayGainMode.Track
    }

    @Test
    fun `a movement without an album gain keeps track mode`() {
        val queue = listOf(movement(1), movement(2, albumGain = null))
        replayGainModeAt(ReplayGainMode.Track, queue, 1) shouldBe ReplayGainMode.Track
    }

    @Test
    fun `album and off modes are unchanged for a work`() {
        val queue = listOf(movement(1), movement(2))
        replayGainModeAt(ReplayGainMode.Album, queue, 0) shouldBe ReplayGainMode.Album
        replayGainModeAt(ReplayGainMode.Off, queue, 0) shouldBe ReplayGainMode.Off
    }

    @Test
    fun `an index outside the queue keeps the mode`() {
        replayGainModeAt(ReplayGainMode.Track, listOf(movement(1), movement(2)), 5) shouldBe ReplayGainMode.Track
    }

    @Test
    fun `off mode ignores both tags but still applies the pre-amp`() {
        replayGainDb(ReplayGainMode.Off, preAmpGain = -3.0, gain) shouldBe (-3.0 plusOrMinus 0.001)
        replayGainDb(ReplayGainMode.Off, preAmpGain = 0.0, gain) shouldBe (0.0 plusOrMinus 0.001)
    }
}
