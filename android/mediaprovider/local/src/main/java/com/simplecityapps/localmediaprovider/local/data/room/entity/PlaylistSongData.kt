package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Embedded

data class PlaylistSongData(
    val playlistSongId: Long,
    val sortOrder: Long,
    @Embedded
    val songData: SongData
)
