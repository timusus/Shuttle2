package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.localmediaprovider.local.ContentsComparator
import com.simplecityapps.mediaprovider.model.AlbumArtist

@Entity(
    tableName = "album_artists",
    indices = [Index("name", unique = true)]
)
data class AlbumArtistData(
    @ColumnInfo(name = "name") val name: String
) : ContentsComparator<AlbumArtistData> {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    override fun areContentsEqual(other: AlbumArtistData): Boolean {
        return name == other.name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumArtistData

        if (name.equals(other.name, true)) return true

        return false
    }

    override fun hashCode(): Int {
        return name.toLowerCase().hashCode()
    }
}


fun AlbumArtist.toAlbumArtistData(): AlbumArtistData {
    val albumArtistData = AlbumArtistData(name)
    albumArtistData.id = id
    return albumArtistData
}

fun List<AlbumData>.toAlbumArtistData(): List<AlbumArtistData> {
    return groupBy { data ->
        AlbumArtistData(data.albumArtistName).apply {
            id = data.albumArtistId
        }
    }
        .map { entry ->
            entry.key
        }
}