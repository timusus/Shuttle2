package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.mediaprovider.model.Song
import java.util.*

@Entity(
    tableName = "songs",
    indices = [
        Index("path", unique = true)
    ]
)
data class SongData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track") val track: Int,
    @ColumnInfo(name = "disc") val disc: Int,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "genres") val genres: List<String>,
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "albumArtist") var albumArtist: String,
    @ColumnInfo(name = "artist") var artist: String,
    @ColumnInfo(name = "album") var album: String,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "mimeType") var mimeType: String,
    @ColumnInfo(name = "lastModified") var lastModified: Date,
    @ColumnInfo(name = "playbackPosition") var playbackPosition: Int = 0,
    @ColumnInfo(name = "playCount") var playCount: Int = 0,
    @ColumnInfo(name = "lastPlayed") var lastPlayed: Date? = null,
    @ColumnInfo(name = "lastCompleted") var lastCompleted: Date? = null,
    @ColumnInfo(name = "blacklisted") var excluded: Boolean = false,
    @ColumnInfo(name = "mediaStoreId") var mediaStoreId: Long? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

fun Song.toSongData(): SongData {
    return SongData(
        name = name,
        track = track,
        disc = disc,
        duration = duration,
        year = year,
        genres = genres,
        path = path,
        albumArtist = albumArtist,
        artist = artist,
        album = album,
        size = size,
        mimeType = mimeType,
        lastModified = lastModified,
        playbackPosition = playbackPosition,
        playCount = playCount,
        lastPlayed = lastPlayed,
        lastCompleted = lastCompleted,
        excluded = false,
        mediaStoreId = mediaStoreId
    ).apply {
        id = this@toSongData.id
    }
}

fun List<Song>.toSongData(): List<SongData> {
    return map { song -> song.toSongData() }
}