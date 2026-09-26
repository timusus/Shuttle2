package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.query.SongQuery

data class SmartPlaylist(
    val nameResId: Int,
    val songQuery: SongQuery
)
