package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType

/** An album or playlist the user downloaded whole; [collectionId] is the album's group key or the playlist id. */
@Entity(
    tableName = "pinned_collections",
    primaryKeys = ["collectionType", "collectionId", "mediaProvider"]
)
data class PinnedCollectionData(
    @ColumnInfo(name = "collectionType") val collectionType: CollectionType,
    @ColumnInfo(name = "collectionId") val collectionId: String,
    @ColumnInfo(name = "mediaProvider") val mediaProviderType: MediaProviderType
) {
    enum class CollectionType {
        Album,
        Playlist
    }
}
