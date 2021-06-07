package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Embedded
import com.simplecityapps.mediaprovider.model.Song

data class PlaylistSongData(val playlistSongId: Long, val sortOrder: Long, @Embedded val song: Song)