package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.query.SongQuery

data class SmartPlaylist(val id: SmartPlaylistId) {
    val songQuery: SongQuery get() = id.songQuery
}
