package com.simplecityapps.playback.spec

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.spec.ClockDriver.Companion.STEP_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.BYTES_PER_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The harness itself: what the spec tests rely on it for. */
@RunWith(RobolectricTestRunner::class)
class PlaybackHarnessTest {
    private val harness = PlaybackHarness()
    private val playback = harness.playbackOperations

    @After
    fun tearDown() {
        harness.release()
    }

    @Test
    fun `a playing song moves on with the player's clock, not with how far ahead the sink has written`() {
        // Two songs, so the sink writes on into the second rather than stopping its track at the end of the first.
        harness.run { playback.addToQueue(listOf(song(1), song(2))) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        steps(20)
        val start = checkNotNull(playback.getProgress())

        steps(50)

        checkNotNull(playback.getProgress()) - start shouldBe 50 * STEP_MS.toInt()
        harness.audioOutput().size shouldBeGreaterThan (start + 500) * BYTES_PER_MS
    }

    /** Moves the player's clock on [count] steps. */
    private fun steps(count: Int) {
        var taken = 0
        harness.runUntil { taken++ == count }
    }
}
