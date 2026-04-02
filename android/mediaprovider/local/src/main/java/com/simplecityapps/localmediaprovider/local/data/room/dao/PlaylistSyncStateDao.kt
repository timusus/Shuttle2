package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSyncState
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: PlaylistSyncState)

    @Update
    suspend fun update(state: PlaylistSyncState)

    @Query("SELECT * FROM playlist_sync_state WHERE playlist_id = :playlistId")
    suspend fun get(playlistId: Long): PlaylistSyncState?

    @Query("SELECT * FROM playlist_sync_state WHERE playlist_id = :playlistId")
    fun observe(playlistId: Long): Flow<PlaylistSyncState?>

    @Query("SELECT * FROM playlist_sync_state WHERE sync_status = :status")
    suspend fun getByStatus(status: PlaylistSyncStatus): List<PlaylistSyncState>

    @Query("SELECT * FROM playlist_sync_state WHERE conflict_detected = 1")
    suspend fun getConflicted(): List<PlaylistSyncState>

    @Query("SELECT * FROM playlist_sync_state WHERE conflict_detected = 1")
    fun observeConflicted(): Flow<List<PlaylistSyncState>>

    @Query("DELETE FROM playlist_sync_state WHERE playlist_id = :playlistId")
    suspend fun delete(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_sync_state WHERE sync_status = :status")
    suspend fun countByStatus(status: PlaylistSyncStatus): Int

    @Query("SELECT COUNT(*) FROM playlist_sync_state WHERE conflict_detected = 1")
    suspend fun countConflicted(): Int
}
