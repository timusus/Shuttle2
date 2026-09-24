package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song

/** A position, in milliseconds, within [song]. */
data class SongPosition(
    val song: Song,
    val positionMs: Int
)
