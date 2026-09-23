package com.simplecityapps.playback.dsp

import androidx.media3.common.Player
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainStreamTracker
import io.kotest.matchers.shouldBe
import org.junit.Test

private val songA = ReplayGain(trackGain = -1.5, albumGain = -7.0)
private val songB = ReplayGain(trackGain = -5.7, albumGain = -7.0)
private val songC = ReplayGain(trackGain = 2.0, albumGain = null)

/**
 * Pins which item's gain applies as the audio sink moves between streams. Each test replays the
 * sequence of calls ExoPlayer's DefaultAudioSink makes for a load, a seek, or a gapless transition.
 */
class ReplayGainStreamTrackerTest {
    private val tracker = ReplayGainStreamTracker()

    /** A load: the playlist is replaced, then the sink is reset and configured for the new item. */
    private fun load(vararg playlist: ReplayGain?) {
        tracker.setPlaylist(playlist.toList())
        tracker.setPlayingIndex(0)
        tracker.onSinkRestarted()
        tracker.onSinkConfigured()
        tracker.onSinkBufferHandled()
        tracker.onProcessorFlushed()
    }

    /** The renderer reaches the next item: reconfigure, drain the old stream, flush, then queue. */
    private fun gaplessTransition() {
        tracker.onSinkConfigured()
        tracker.onSinkBufferHandled()
        tracker.onProcessorFlushed()
    }

    @Test
    fun `a loaded item's gain applies from its first buffer`() {
        load(songA, songB)

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `the next item's gain applies from the stream boundary, before the player reports the transition`() {
        load(songA, songB)

        gaplessTransition()

        tracker.currentReplayGain() shouldBe songB
    }

    @Test
    fun `old stream audio drained after the reconfigure keeps the old gain`() {
        load(songA, songB)

        tracker.onSinkConfigured()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `the player catching up with the transition keeps the next item's gain`() {
        load(songA, songB)
        gaplessTransition()

        tracker.setPlayingIndex(1)

        tracker.currentReplayGain() shouldBe songB
    }

    @Test
    fun `successive gapless transitions follow the playlist`() {
        load(songA, songB)
        gaplessTransition()
        tracker.setPlayingIndex(1)
        tracker.setPlaylist(listOf(songA, songB, songC))

        gaplessTransition()

        tracker.currentReplayGain() shouldBe songC
    }

    @Test
    fun `a seek within the playing item keeps its gain`() {
        load(songA, songB)

        tracker.onSinkRestarted()
        tracker.onSinkConfigured()
        tracker.onSinkBufferHandled()
        tracker.onProcessorFlushed()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `a seek before the player reports the transition returns to the playing item's gain`() {
        load(songA, songB)
        gaplessTransition()

        tracker.onSinkRestarted()
        tracker.onProcessorFlushed()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `a seek between the reconfigure and the flush cancels the pending switch`() {
        load(songA, songB)
        tracker.onSinkConfigured()

        tracker.onSinkRestarted()
        tracker.onProcessorFlushed()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `flushes that are not stream boundaries keep the gain`() {
        load(songA, songB)

        // A playback speed change drains and flushes the processors without a reconfigure.
        tracker.onProcessorFlushed()
        tracker.onSinkBufferHandled()
        tracker.onProcessorFlushed()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `repeat one keeps the item's gain as it loops`() {
        load(songA, songB)
        tracker.setRepeatMode(Player.REPEAT_MODE_ONE)

        gaplessTransition()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `repeat all wraps to the first item's gain`() {
        load(songA, songB)
        tracker.setRepeatMode(Player.REPEAT_MODE_ALL)
        gaplessTransition()
        tracker.setPlayingIndex(1)

        gaplessTransition()

        tracker.currentReplayGain() shouldBe songA
    }

    @Test
    fun `an item without ReplayGain tags has no gain`() {
        load(null, songB)

        tracker.currentReplayGain() shouldBe null
    }

    @Test
    fun `a new load replaces the previous playlist`() {
        load(songA, songB)
        gaplessTransition()

        load(songC)

        tracker.currentReplayGain() shouldBe songC
    }
}
