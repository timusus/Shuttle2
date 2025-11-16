package com.simplecityapps.mediaprovider.repository.downloads

import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing song downloads for offline playback
 */
interface DownloadRepository {

    /**
     * Get download information for a specific song
     */
    suspend fun getDownload(song: Song): DownloadData?

    /**
     * Observe download information for a specific song
     */
    fun observeDownload(song: Song): Flow<DownloadData?>

    /**
     * Get download information for multiple songs
     */
    suspend fun getDownloads(songs: List<Song>): List<DownloadData>

    /**
     * Observe download information for multiple songs
     */
    fun observeDownloads(songs: List<Song>): Flow<List<DownloadData>>

    /**
     * Get all downloads in a specific state
     */
    suspend fun getDownloadsByState(state: DownloadState): List<DownloadData>

    /**
     * Observe all downloads in a specific state
     */
    fun observeDownloadsByState(state: DownloadState): Flow<List<DownloadData>>

    /**
     * Get all downloads
     */
    suspend fun getAllDownloads(): List<DownloadData>

    /**
     * Observe all downloads
     */
    fun observeAllDownloads(): Flow<List<DownloadData>>

    /**
     * Get currently active downloads (queued or downloading)
     */
    suspend fun getActiveDownloads(): List<DownloadData>

    /**
     * Observe currently active downloads
     */
    fun observeActiveDownloads(): Flow<List<DownloadData>>

    /**
     * Queue a song for download
     */
    suspend fun queueDownload(song: Song)

    /**
     * Queue multiple songs for download
     */
    suspend fun queueDownloads(songs: List<Song>)

    /**
     * Update download progress
     */
    suspend fun updateDownloadProgress(
        song: Song,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long
    )

    /**
     * Mark download as completed
     */
    suspend fun markDownloadCompleted(song: Song, localPath: String, totalBytes: Long)

    /**
     * Mark download as failed
     */
    suspend fun markDownloadFailed(song: Song, errorMessage: String)

    /**
     * Pause a download
     */
    suspend fun pauseDownload(song: Song)

    /**
     * Resume a paused download
     */
    suspend fun resumeDownload(song: Song)

    /**
     * Cancel and remove a download
     */
    suspend fun cancelDownload(song: Song)

    /**
     * Cancel and remove multiple downloads
     */
    suspend fun cancelDownloads(songs: List<Song>)

    /**
     * Remove a completed download (delete the file and database entry)
     */
    suspend fun removeDownload(song: Song)

    /**
     * Remove multiple completed downloads
     */
    suspend fun removeDownloads(songs: List<Song>)

    /**
     * Remove all downloads in a specific state
     */
    suspend fun removeDownloadsByState(state: DownloadState)

    /**
     * Remove all downloads
     */
    suspend fun removeAllDownloads()

    /**
     * Get total size of all downloaded files
     */
    suspend fun getTotalDownloadedSize(): Long

    /**
     * Get count of downloads by state
     */
    suspend fun getDownloadCountByState(state: DownloadState): Int

    /**
     * Observe count of downloads by state
     */
    fun observeDownloadCountByState(state: DownloadState): Flow<Int>

    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(song: Song): Boolean

    /**
     * Get the local file path for a downloaded song, or null if not downloaded
     */
    suspend fun getLocalPath(song: Song): String?
}
