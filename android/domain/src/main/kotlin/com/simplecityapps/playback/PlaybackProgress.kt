package com.simplecityapps.playback

/**
 * The current track's seek position and duration, in milliseconds.
 */
data class PlaybackProgress(
    val position: Int,
    val duration: Int
)
