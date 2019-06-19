package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_song_join",
    indices = [
        Index("playlistId"),
        Index("songId")
    ],
    foreignKeys = [
        ForeignKey(
            onDelete = ForeignKey.CASCADE,
            entity = PlaylistData::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"]
        ),
        ForeignKey(
            onDelete = ForeignKey.CASCADE,
            entity = SongData::class,
            parentColumns = ["id"],
            childColumns = ["songId"]
        )
    ]
)
data class PlaylistSongJoin(
    val playlistId: Long,
    val songId: Long
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0
}