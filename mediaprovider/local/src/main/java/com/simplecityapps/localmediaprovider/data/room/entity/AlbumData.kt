package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var songs = arrayListOf<SongData>()
}