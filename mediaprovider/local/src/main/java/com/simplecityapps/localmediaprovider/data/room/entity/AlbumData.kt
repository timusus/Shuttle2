package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*

@Entity(
    tableName = "albums",
    indices = [Index("albumArtistId", "name", unique = true)],
    foreignKeys = [(ForeignKey(
        entity = AlbumArtistData::class,
        parentColumns = ["id"],
        childColumns = ["albumArtistId"]
    ))]
)
data class AlbumData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var songs = mutableListOf<SongData>()
}

fun List<SongData>.toAlbumData(): List<AlbumData> {
    return groupBy { data -> Pair(data.albumName, data.albumArtistName) }
        .map { entry ->
            val albumData = AlbumData(name = entry.key.first)
            val sampleSong = entry.value.first()
            albumData.id = sampleSong.albumId
            albumData.albumArtistId = sampleSong.albumArtistId
            albumData.albumArtistName = entry.key.second
            albumData.songs.addAll(entry.value)
            albumData
        }
}