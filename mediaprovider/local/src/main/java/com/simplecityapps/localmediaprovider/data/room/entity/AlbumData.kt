package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*

@Entity(
    tableName = "albums",
    indices = [Index("albumArtistId", "name", unique = true)],
    foreignKeys = [(ForeignKey(entity = AlbumArtistData::class, parentColumns = ["id"], childColumns = ["albumArtistId"], onDelete = ForeignKey.CASCADE))]
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
    var songs = arrayListOf<SongData>()
}