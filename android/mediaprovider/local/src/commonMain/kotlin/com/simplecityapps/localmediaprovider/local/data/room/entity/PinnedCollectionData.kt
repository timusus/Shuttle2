package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.simplecityapps.shuttle.model.MediaProviderType

/**
 * An album or playlist the user downloaded as a whole. Pinned collections download their new songs
 * after each import.
 *
 * [collectionId] is the album's group key, as [com.simplecityapps.shuttle.model.AlbumGroupKey.encode] writes it (keys
 * written before the album identity rule, #637, are moved once by [com.simplecityapps.localmediaprovider.local.repository.AlbumKeyMigration]),
 * or the playlist's id, as a string.
 */
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
