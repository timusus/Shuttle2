package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "album_artists")
data class AlbumArtistData(
    @ColumnInfo(name = "name") val name: String
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albums = arrayListOf<AlbumData>()

    @Ignore
    var songs = arrayListOf<SongData>()
}