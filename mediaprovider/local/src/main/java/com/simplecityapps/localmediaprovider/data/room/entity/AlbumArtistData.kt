package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*

@Entity(
    tableName = "album_artists",
    indices = [Index("name", unique = true)]
)
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