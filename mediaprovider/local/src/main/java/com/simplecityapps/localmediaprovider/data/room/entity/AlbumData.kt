package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.ContentsComparator

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
) : ContentsComparator<AlbumData> {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albumArtistName: String = ""

    @Ignore
    var songs = listOf<SongData>()

    override fun areContentsEqual(other: AlbumData): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumData

        if (!name.equals(other.name, true)) return false
        if (!albumArtistName.equals(other.albumArtistName, true)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.toLowerCase().hashCode()
        result = 31 * result + albumArtistName.toLowerCase().hashCode()
        return result
    }
}


fun List<SongData>.toAlbumData(): List<AlbumData> {
    return groupBy { data ->
        AlbumData(data.albumName, data.albumId).apply {
            albumArtistId = data.albumArtistId
            albumArtistName = data.albumArtistName
        }
    }
        .map { entry ->
            entry.key.apply {
                songs = entry.value
            }
        }
}