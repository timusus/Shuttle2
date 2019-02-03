package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

@Entity(
    tableName = "songs",
    indices = [
        Index("path", unique = true),
        Index("albumArtistId"),
        Index("albumId")
    ],
    foreignKeys = [
        (ForeignKey(
            entity = AlbumArtistData::class,
            parentColumns = ["id"],
            childColumns = ["albumArtistId"]
        )),
        (ForeignKey(
            entity = AlbumData::class,
            parentColumns = ["id"],
            childColumns = ["albumId"]
        ))
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
    @ColumnInfo(name = "albumId") var albumId: Long = 0,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "lastModified") var lastModified: Date
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var albumName: String = ""
}

fun AudioFile.toSongData(): SongData {
    val songData = SongData(name, track, trackTotal, disc, discTotal, duration, year, path, 0, 0, size, Date(lastModified))
    songData.albumArtistName = albumArtistName
    songData.albumName = albumName
    return songData
}

fun Song.toSongData(): SongData {
    val songData = SongData(name, track, trackTotal, disc, discTotal, duration, year, path, albumArtistId, albumId, size, lastModified)
    songData.id = id
    songData.albumArtistName = albumArtistName
    songData.albumName = albumName
    return songData
}
