package com.simplecityapps.playback.mediasession

import android.support.v4.media.session.PlaybackStateCompat
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PositionAnchor
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The media session publishes each [PositionAnchor] as a playback state with the anchor's own
 * position, update time and speed, and is active only while loading or playing.
 */
class SessionPlaybackStateTest {
    private fun anchor(
        state: PlaybackState,
        positionMs: Int? = 42_000,
        elapsedRealtimeMs: Long = 10_000,
        speed: Float = 1.5f
    ) = PositionAnchor(state, positionMs, elapsedRealtimeMs, speed)

    @Test
    fun `a loading anchor is published as buffering and keeps the session active`() {
        anchor(PlaybackState.Loading).toSessionPlaybackState() shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING, positionMs = 42_000, speed = 1.5f, updateTimeMs = 10_000, isActive = true)
    }

    @Test
    fun `a playing anchor is published as playing and keeps the session active`() {
        anchor(PlaybackState.Playing).toSessionPlaybackState() shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, positionMs = 42_000, speed = 1.5f, updateTimeMs = 10_000, isActive = true)
    }

    @Test
    fun `a paused anchor is published as paused and deactivates the session`() {
        anchor(PlaybackState.Paused).toSessionPlaybackState() shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, positionMs = 42_000, speed = 1.5f, updateTimeMs = 10_000, isActive = false)
    }

    @Test
    fun `a stopped playback, which has no position, is published as paused at an unknown position`() {
        // PlaybackState has no stopped state: a playback that has nothing loaded is paused with no position.
        anchor(PlaybackState.Paused, positionMs = null).toSessionPlaybackState() shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, positionMs = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed = 1.5f, updateTimeMs = 10_000, isActive = false)
    }

    @Test
    fun `a new track's start is published at position zero with the anchor's update time`() {
        anchor(PlaybackState.Loading, positionMs = 0, elapsedRealtimeMs = 20_000, speed = 1f).toSessionPlaybackState() shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_BUFFERING, positionMs = 0, speed = 1f, updateTimeMs = 20_000, isActive = true)
    }
}
