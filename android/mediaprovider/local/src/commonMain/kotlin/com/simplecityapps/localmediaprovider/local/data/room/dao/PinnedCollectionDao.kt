package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplecityapps.localmediaprovider.local.data.room.entity.PinnedCollectionData
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedCollectionDao {
    @Query("SELECT * FROM pinned_collections")
    fun getAll(): Flow<List<PinnedCollectionData>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pinnedCollection: PinnedCollectionData)

    @Delete
    suspend fun delete(pinnedCollection: PinnedCollectionData)
}
