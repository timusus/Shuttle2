package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Embedded

data class PlaylistCoverSongData(
    val sortOrder: Long,
    @Embedded
    val songData: SongData
)
