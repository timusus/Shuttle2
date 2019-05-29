package com.simplecityapps.localmediaprovider.data.room.entity

import androidx.room.*
import com.simplecityapps.localmediaprovider.ContentsComparator

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

    @Ignore var songs = listOf<SongData>()

    override fun areContentsEqual(other: AlbumData): Boolean {
        return name == other.name
                && albumArtistName == other.albumArtistName
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumData

        if (name == other.name && albumArtistName == other.albumArtistName) {
            return true
        } else if (songs.size == other.songs.size && songs.containsAll(other.songs)) {
            return true
        }

        return false
    }

    override fun hashCode(): Int {
        var result = name.toLowerCase().hashCode()
        result = 31 * result + albumArtistName.toLowerCase().hashCode()
        result = 31 * result + songs.hashCode()
        return result
    }
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
            entry.key.apply {
                songs = entry.value
            }
        }
}