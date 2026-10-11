package com.simplecityapps.playback

/**
 * The current track's seek position and duration, in milliseconds. [buffered] is how far the player has loaded, or 0
 * when there is nothing to show: unknown, or loaded to the end as a local file is.
 */
data class PlaybackProgress(
    val position: Int,
    val duration: Int,
    val buffered: Int = 0
)
