package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists"
)
data class PlaylistData(
    @ColumnInfo(name = "name") val name: String
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    @Ignore var songs = listOf<SongData>()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlaylistData

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}