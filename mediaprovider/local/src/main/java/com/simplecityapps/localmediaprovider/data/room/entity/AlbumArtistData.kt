package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.ContentsComparator

@Entity(
    tableName = "album_artists",
    indices = [Index("name", unique = true)]
)
data class AlbumArtistData(
    @ColumnInfo(name = "name") val name: String
) : ContentsComparator<AlbumArtistData> {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    @Ignore
    var albums = listOf<AlbumData>()

    override fun areContentsEqual(other: AlbumArtistData): Boolean {
        return true
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumArtistData

        if (!name.equals(other.name, true)) return false

        return true
    }

    override fun hashCode(): Int {
        return name.toLowerCase().hashCode()
    }
}


fun List<AlbumData>.toAlbumArtistData(): List<AlbumArtistData> {
    return groupBy { data ->
        AlbumArtistData(data.albumArtistName).apply {
            id = data.albumArtistId
        }
    }
        .map { entry ->
            entry.key.apply {
                albums = filter { data -> data.albumArtistName.equals(name, true) }.toList()
            }
        }
}