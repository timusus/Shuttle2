package com.simplecityapps.localmediaprovider.local.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.shuttle.model.DownloadState
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for download operations
 */
@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads WHERE song_id = :songId LIMIT 1")
    suspend fun getDownload(songId: Long): DownloadData?

    @Query("SELECT * FROM downloads WHERE song_id = :songId LIMIT 1")
    fun observeDownload(songId: Long): Flow<DownloadData?>

    @Query("SELECT * FROM downloads WHERE song_id IN (:songIds)")
    suspend fun getDownloads(songIds: List<Long>): List<DownloadData>

    @Query("SELECT * FROM downloads WHERE song_id IN (:songIds)")
    fun observeDownloads(songIds: List<Long>): Flow<List<DownloadData>>

    @Query("SELECT * FROM downloads WHERE download_state = :state")
    suspend fun getDownloadsByState(state: DownloadState): List<DownloadData>

    @Query("SELECT * FROM downloads WHERE download_state = :state")
    fun observeDownloadsByState(state: DownloadState): Flow<List<DownloadData>>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<DownloadData>

    @Query("SELECT * FROM downloads")
    fun observeAllDownloads(): Flow<List<DownloadData>>

    @Query("SELECT * FROM downloads WHERE download_state = :state1 OR download_state = :state2")
    suspend fun getActiveDownloads(
        state1: DownloadState = DownloadState.DOWNLOADING,
        state2: DownloadState = DownloadState.QUEUED
    ): List<DownloadData>

    @Query("SELECT * FROM downloads WHERE download_state = :state1 OR download_state = :state2")
    fun observeActiveDownloads(
        state1: DownloadState = DownloadState.DOWNLOADING,
        state2: DownloadState = DownloadState.QUEUED
    ): Flow<List<DownloadData>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadData): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadData>)

    @Update
    suspend fun update(download: DownloadData)

    @Update
    suspend fun updateAll(downloads: List<DownloadData>)

    @Delete
    suspend fun delete(download: DownloadData)

    @Query("DELETE FROM downloads WHERE song_id = :songId")
    suspend fun deleteBySongId(songId: Long)

    @Query("DELETE FROM downloads WHERE song_id IN (:songIds)")
    suspend fun deleteBySongIds(songIds: List<Long>)

    @Query("DELETE FROM downloads WHERE download_state = :state")
    suspend fun deleteByState(state: DownloadState)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("UPDATE downloads SET download_state = :newState WHERE song_id = :songId")
    suspend fun updateDownloadState(songId: Long, newState: DownloadState)

    @Query("UPDATE downloads SET download_state = :newState WHERE song_id IN (:songIds)")
    suspend fun updateDownloadStates(songIds: List<Long>, newState: DownloadState)

    @Query(
        """
        UPDATE downloads
        SET download_progress = :progress,
            downloaded_bytes = :downloadedBytes,
            total_bytes = :totalBytes,
            updated_at = :updatedAt
        WHERE song_id = :songId
    """
    )
    suspend fun updateDownloadProgress(
        songId: Long,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        updatedAt: java.util.Date
    )

    @Query("SELECT SUM(total_bytes) FROM downloads WHERE download_state = :state")
    suspend fun getTotalDownloadedSize(state: DownloadState = DownloadState.COMPLETED): Long?

    @Query("SELECT COUNT(*) FROM downloads WHERE download_state = :state")
    suspend fun getDownloadCountByState(state: DownloadState): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE download_state = :state")
    fun observeDownloadCountByState(state: DownloadState): Flow<Int>
}
