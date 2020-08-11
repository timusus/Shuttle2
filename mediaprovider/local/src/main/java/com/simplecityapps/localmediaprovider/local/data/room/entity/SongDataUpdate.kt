package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.*

data class SongDataUpdate(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track") val track: Int,
    @ColumnInfo(name = "disc") val disc: Int,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "albumArtist") var albumArtist: String,
    @ColumnInfo(name = "album") var album: String,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "mimeType") var mimeType: String,
    @ColumnInfo(name = "lastModified") var lastModified: Date
)

fun SongData.toSongDataUpdate(): SongDataUpdate {
    return SongDataUpdate(
        id = id, name = name,
        track = track,
        disc = disc,
        duration = duration,
        year = year,
        albumArtist = albumArtist,
        album = album,
        size = size,
        mimeType = mimeType,
        lastModified = lastModified
    )
}