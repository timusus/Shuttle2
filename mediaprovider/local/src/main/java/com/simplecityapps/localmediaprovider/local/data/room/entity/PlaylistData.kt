package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists"
)
data class PlaylistData(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long? = null
) {

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