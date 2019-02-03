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
}


fun List<AlbumData>.toAlbumArtistData(): List<AlbumArtistData> {
    return groupBy { data -> data.albumArtistName }
        .map { entry ->
            val albumArtistData = AlbumArtistData(name = entry.key)
            val albums = filter { data -> data.albumArtistName == albumArtistData.name }
            albumArtistData.id = albums.first().albumArtistId
            albumArtistData.albums.addAll(albums)
            albumArtistData
        }
}