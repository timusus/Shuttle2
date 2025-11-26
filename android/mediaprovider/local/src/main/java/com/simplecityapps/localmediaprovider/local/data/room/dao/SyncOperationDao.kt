package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncOperation
import com.simplecityapps.localmediaprovider.local.data.room.entity.SyncStatus
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Insert
    suspend fun insert(operation: SyncOperation): Long

    @Insert
    suspend fun insertAll(operations: List<SyncOperation>)

    @Update
    suspend fun update(operation: SyncOperation)

    @Query("SELECT * FROM sync_operations WHERE id = :operationId")
    suspend fun get(operationId: Long): SyncOperation?

    @Query("SELECT * FROM sync_operations WHERE status = :status ORDER BY priority DESC, created_at ASC")
    suspend fun getByStatus(status: SyncStatus): List<SyncOperation>

    @Query("SELECT * FROM sync_operations WHERE status IN (:statuses) ORDER BY priority DESC, created_at ASC")
    fun observeByStatuses(statuses: List<SyncStatus>): Flow<List<SyncOperation>>

    @Query("SELECT * FROM sync_operations WHERE playlist_id = :playlistId AND status = :status")
    suspend fun getByPlaylistAndStatus(playlistId: Long, status: SyncStatus): List<SyncOperation>

    @Query("SELECT * FROM sync_operations WHERE playlist_id = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<SyncOperation>

    @Query("SELECT * FROM sync_operations WHERE media_provider_type = :mediaProviderType AND status = :status")
    suspend fun getByProviderAndStatus(
        mediaProviderType: MediaProviderType,
        status: SyncStatus
    ): List<SyncOperation>

    @Query("DELETE FROM sync_operations WHERE id = :operationId")
    suspend fun delete(operationId: Long)

    @Query("DELETE FROM sync_operations WHERE playlist_id = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("DELETE FROM sync_operations WHERE status = :status")
    suspend fun deleteByStatus(status: SyncStatus)

    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = :status")
    suspend fun countByStatus(status: SyncStatus): Int

    @Query("SELECT COUNT(*) FROM sync_operations WHERE playlist_id = :playlistId AND status IN (:statuses)")
    suspend fun countByPlaylistAndStatuses(playlistId: Long, statuses: List<SyncStatus>): Int
}
