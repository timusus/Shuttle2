package com.simplecityapps.playback

/**
 * Where playback was at a known instant: [positionMs] at [elapsedRealtimeMs] (on the
 * `SystemClock.elapsedRealtime` timebase), advancing at [speed] while [state] is
 * [PlaybackState.Playing]. Consumers such as media controllers extrapolate the position between
 * anchors, so a new anchor is only published on a discontinuity (a state change, seek, speed change,
 * track change or playback switch), never on a plain progress tick.
 *
 * @param positionMs the position in milliseconds, or null if the playback doesn't know it
 */
data class PositionAnchor(
    val state: PlaybackState,
    val positionMs: Int?,
    val elapsedRealtimeMs: Long,
    val speed: Float
)
