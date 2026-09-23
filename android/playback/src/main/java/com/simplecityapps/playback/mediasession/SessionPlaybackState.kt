package com.simplecityapps.playback.mediasession

import android.support.v4.media.session.PlaybackStateCompat
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PositionAnchor

/**
 * What the media session publishes for a [PositionAnchor]: the [PlaybackStateCompat.Builder.setState]
 * arguments, and whether the session is active. Kept free of Android types so the mapping is
 * testable on the JVM.
 *
 * @param updateTimeMs the anchor's own clock time, so controllers extrapolate the position from
 * when it was actually read rather than from when it reached the session
 */
data class SessionPlaybackState(
    @PlaybackStateCompat.State val state: Int,
    val positionMs: Long,
    val speed: Float,
    val updateTimeMs: Long,
    val isActive: Boolean
)

fun PositionAnchor.toSessionPlaybackState() = SessionPlaybackState(
    state =
        when (state) {
            is PlaybackState.Loading -> PlaybackStateCompat.STATE_BUFFERING
            is PlaybackState.Playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        },
    positionMs = positionMs?.toLong() ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
    speed = speed,
    updateTimeMs = elapsedRealtimeMs,
    isActive = state == PlaybackState.Loading || state == PlaybackState.Playing
)
