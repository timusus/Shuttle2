package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.local.ContentsComparator
import com.simplecityapps.mediaprovider.model.Album

@Entity(
    tableName = "albums",
    indices = [Index("albumArtistId", "name", unique = true)],
    foreignKeys = [(ForeignKey(
        entity = AlbumArtistData::class,
        parentColumns = ["id"],
        childColumns = ["albumArtistId"],
        onDelete = ForeignKey.CASCADE
    ))]
)
data class AlbumData(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "albumArtistId") var albumArtistId: Long = 0
) : ContentsComparator<AlbumData> {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    @Ignore var albumArtistName: String = ""

    override fun areContentsEqual(other: AlbumData): Boolean {
        return name == other.name
                && albumArtistName == other.albumArtistName
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumData

        if (name.equals(other.name, true)
            && albumArtistName.equals(other.albumArtistName, true)
        ) {
            return true
        }

        return false
    }

    override fun hashCode(): Int {
        var result = name.toLowerCase().hashCode()
        result = 31 * result + albumArtistName.toLowerCase().hashCode()
        return result
    }
}

fun Album.toAlbumData(): AlbumData {
    val albumData = AlbumData(name, albumArtistId)
    albumData.id = id
    albumData.albumArtistName = albumArtistName
    return albumData
}

fun List<SongData>.toAlbumData(): List<AlbumData> {
    return groupBy { data ->
        AlbumData(data.albumName, data.albumArtistId).apply {
            id = data.albumId
            albumArtistId = data.albumArtistId
            albumArtistName = data.albumArtistName
        }
    }
        .map { entry ->
            entry.key
        }
}