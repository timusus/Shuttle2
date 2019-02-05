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
        return name == other.name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumArtistData

        if (name == other.name) {
            return true
        } else {
            if (albums.size == other.albums.size && albums.containsAll(other.albums)) {
                return true
            }
        }

        return false
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + albums.hashCode()
        return result
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
                albums = filter { data -> data.albumArtistName == name }.toList()
            }
        }
}