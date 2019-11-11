package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.local.ContentsComparator
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
            childColumns = ["albumArtistId"],
            onDelete = ForeignKey.CASCADE
        )),
        (ForeignKey(
            entity = AlbumData::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        ))
    ]
)
data class SongData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track") val track: Int,
    @ColumnInfo(name = "disc") val disc: Int,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0,
    @ColumnInfo(name = "albumId") var albumId: Long = 0,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "mimeType") var mimeType: String,
    @ColumnInfo(name = "lastModified") var lastModified: Date,
    @ColumnInfo(name = "playbackPosition") var playbackPosition: Int = 0,
    @ColumnInfo(name = "playCount") var playCount: Int = 0,
    @ColumnInfo(name = "lastPlayed") var lastPlayed: Date? = null,
    @ColumnInfo(name = "lastCompleted") var lastCompleted: Date? = null
) : ContentsComparator<SongData> {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var albumName: String = ""

    override fun areContentsEqual(other: SongData): Boolean {
        // Todo: The track/disc check can be removed.
        //  This is a fix for a temporary issue, due to a change in how track/disc numbers are imported from the MediaStore.
        //  This just enables users with out-dated track/disc parsing logic to have their library update.
        return lastModified == other.lastModified && track == other.track && disc == other.disc
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

fun Song.toSongData(): SongData {
    return SongData(
        name,
        track,
        disc,
        duration,
        year,
        path,
        albumArtistId,
        albumId,
        size,
        mimeType,
        lastModified,
        playbackPosition,
        playCount,
        lastPlayed,
        lastCompleted
    ).apply {
        id = this@toSongData.id
        albumArtistName = this@toSongData.albumArtistName
        albumName = this@toSongData.albumName
    }
}

fun List<Song>.toSongData(): List<SongData> {
    return map { song -> song.toSongData() }
}