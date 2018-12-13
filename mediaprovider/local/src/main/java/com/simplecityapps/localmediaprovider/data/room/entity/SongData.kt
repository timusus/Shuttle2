package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.model.AudioFile

@Entity(
    tableName = "songs",
    indices = [
        Index("path", unique = true),
        Index("albumArtistId"),
        Index("albumId")
    ],
    foreignKeys = [
        (ForeignKey(entity = AlbumArtistData::class, parentColumns = ["id"], childColumns = ["albumArtistId"], onDelete = ForeignKey.CASCADE)),
        (ForeignKey(entity = AlbumData::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE))
    ]
)
data class SongData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track") val track: Int,
    @ColumnInfo(name = "trackTotal") val trackTotal: Int,
    @ColumnInfo(name = "disc") val disc: Int,
    @ColumnInfo(name = "discTotal") val discTotal: Int,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0,
    @ColumnInfo(name = "albumId") var albumId: Long = 0
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var albumName: String = ""

}

fun AudioFile.toSongData(): SongData {
    val songData = SongData(name, track, trackTotal, disc, discTotal, duration, year, path)
    songData.albumArtistName = albumArtistName
    songData.albumName = albumName
    return songData
}