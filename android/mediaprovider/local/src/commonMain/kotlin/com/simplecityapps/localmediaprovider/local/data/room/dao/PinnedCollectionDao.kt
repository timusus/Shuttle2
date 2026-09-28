package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedCollectionDao {
    @Query("SELECT * FROM pinned_collections")
    fun getAll(): Flow<List<PinnedCollectionData>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pinnedCollection: PinnedCollectionData)

    @Delete
    suspend fun delete(pinnedCollection: PinnedCollectionData)

    /**
     * Moves [pinned] to [collectionId] in one statement, so it can't be left half moved. When a collection is already
     * pinned under [collectionId] (two old keys naming one album now), this row replaces it: one pin remains.
     */
    @Query(
        "UPDATE OR REPLACE pinned_collections SET collectionId = :collectionId " +
            "WHERE collectionType = :collectionType AND collectionId = :from AND mediaProvider = :mediaProviderType"
    )
    suspend fun move(
        collectionType: PinnedCollectionData.CollectionType,
        from: String,
        mediaProviderType: MediaProviderType,
        collectionId: String
    ): Int

    @Query("SELECT * FROM pinned_collections WHERE collectionType = :collectionType")
    suspend fun ofType(collectionType: PinnedCollectionData.CollectionType): List<PinnedCollectionData>
}
