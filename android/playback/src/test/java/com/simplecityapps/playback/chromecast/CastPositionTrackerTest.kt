package com.simplecityapps.playback.chromecast

import io.kotest.matchers.shouldBe
import org.junit.Test

/** [CastPlayback] reports a discontinuity only for a Cast status that departs from the extrapolated position. */
class CastPositionTrackerTest {
    private var now = 100_000L

    private val tracker = CastPositionTracker(elapsedRealtime = { now })

    private fun status(
        positionMs: Long,
        rate: Double = 1.0,
        isPlaying: Boolean = true,
        mediaId: String? = "song-1"
    ): Boolean = tracker.isDiscontinuity(positionMs, rate, isPlaying, mediaId)

    @Test
    fun `the first status is a discontinuity`() {
        status(positionMs = 5_000) shouldBe true
    }

    @Test
    fun `steady playback updates are not discontinuities`() {
        status(positionMs = 5_000)

        now += 1_000
        status(positionMs = 6_000) shouldBe false
        now += 1_000
        status(positionMs = 7_300) shouldBe false
        now += 1_000
        status(positionMs = 7_800) shouldBe false
    }

    @Test
    fun `steady playback at another rate extrapolates at that rate`() {
        status(positionMs = 5_000, rate = 2.0)

        now += 5_000
        status(positionMs = 15_000, rate = 2.0) shouldBe false
    }

    @Test
    fun `drift beyond the tolerance is a discontinuity and becomes the new anchor`() {
        status(positionMs = 5_000)

        now += 10_000
        status(positionMs = 16_001) shouldBe true

        now += 1_000
        status(positionMs = 17_001) shouldBe false
    }

    @Test
    fun `drift within the tolerance is not a discontinuity`() {
        status(positionMs = 5_000)

        now += 10_000
        status(positionMs = 16_000) shouldBe false
        status(positionMs = 14_000) shouldBe false
    }

    @Test
    fun `a paused position doesn't advance with time`() {
        status(positionMs = 5_000, isPlaying = false)

        now += 60_000
        status(positionMs = 5_000, isPlaying = false) shouldBe false
    }

    @Test
    fun `an external seek while paused is a discontinuity`() {
        status(positionMs = 5_000, isPlaying = false)

        now += 3_000
        status(positionMs = 42_000, isPlaying = false) shouldBe true
    }

    @Test
    fun `a rate change is a discontinuity`() {
        status(positionMs = 5_000, rate = 1.0)

        now += 1_000
        status(positionMs = 6_000, rate = 1.5) shouldBe true
    }

    @Test
    fun `pausing and resuming are discontinuities`() {
        status(positionMs = 5_000, isPlaying = true)

        now += 1_000
        status(positionMs = 6_000, isPlaying = false) shouldBe true
        now += 1_000
        status(positionMs = 6_000, isPlaying = true) shouldBe true
    }

    @Test
    fun `a track change is a discontinuity even at the extrapolated position`() {
        status(positionMs = 0, mediaId = "song-1")

        now += 1_000
        status(positionMs = 1_000, mediaId = "song-2") shouldBe true
    }

    @Test
    fun `the status after a reset is a discontinuity`() {
        status(positionMs = 5_000)

        tracker.reset()
        now += 1_000
        status(positionMs = 6_000) shouldBe true
    }
}
