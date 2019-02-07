package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.ContentsComparator
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
    @ColumnInfo(name = "disc") val disc: Int,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0,
    @ColumnInfo(name = "albumId") var albumId: Long = 0,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "lastModified") var lastModified: Date
) : ContentsComparator<SongData> {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var albumName: String = ""

    override fun areContentsEqual(other: SongData): Boolean {
        return lastModified == other.lastModified
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SongData

        if (path != other.path) return false

        return true
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}

fun AudioFile.toSongData(): SongData {
    return SongData(name, track, disc, duration, year, path, 0, 0, size, Date(lastModified)).apply {
        albumArtistName = this@toSongData.albumArtistName
        albumName = this@toSongData.albumName
    }
}

fun Song.toSongData(): SongData {
    return SongData(name, track, disc, duration, year, path, albumArtistId, albumId, size, lastModified).apply {
        id = this@toSongData.id
        albumArtistName = this@toSongData.albumArtistName
        albumName = this@toSongData.albumName
    }
}