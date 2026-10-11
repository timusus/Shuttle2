package com.simplecityapps.playback

/**
 * The current track's seek position and duration, in milliseconds. [buffered] is how far the player has loaded, or 0
 * when there is nothing to show: not a network stream, unknown, or loaded to the end.
 */
data class PlaybackProgress(
    val position: Int,
    val duration: Int,
    val buffered: Int = 0
)
