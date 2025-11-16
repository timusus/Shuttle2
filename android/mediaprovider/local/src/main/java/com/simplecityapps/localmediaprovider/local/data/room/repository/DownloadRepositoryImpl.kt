package com.simplecityapps.localmediaprovider.local.data.room.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.DownloadDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.DownloadData
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of DownloadRepository using Room database
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao
) : DownloadRepository {

    override suspend fun getDownload(song: Song): DownloadData? {
        return downloadDao.getDownload(song.id)
    }

    override fun observeDownload(song: Song): Flow<DownloadData?> {
        return downloadDao.observeDownload(song.id)
    }

    override suspend fun getDownloads(songs: List<Song>): List<DownloadData> {
        return downloadDao.getDownloads(songs.map { it.id })
    }

    override fun observeDownloads(songs: List<Song>): Flow<List<DownloadData>> {
        return downloadDao.observeDownloads(songs.map { it.id })
    }

    override suspend fun getDownloadsByState(state: DownloadState): List<DownloadData> {
        return downloadDao.getDownloadsByState(state)
    }

    override fun observeDownloadsByState(state: DownloadState): Flow<List<DownloadData>> {
        return downloadDao.observeDownloadsByState(state)
    }

    override suspend fun getAllDownloads(): List<DownloadData> {
        return downloadDao.getAllDownloads()
    }

    override fun observeAllDownloads(): Flow<List<DownloadData>> {
        return downloadDao.observeAllDownloads()
    }

    override suspend fun getActiveDownloads(): List<DownloadData> {
        return downloadDao.getActiveDownloads()
    }

    override fun observeActiveDownloads(): Flow<List<DownloadData>> {
        return downloadDao.observeActiveDownloads()
    }

    override suspend fun queueDownload(song: Song) {
        val existing = downloadDao.getDownload(song.id)
        if (existing == null) {
            val download = DownloadData(
                songId = song.id,
                downloadState = DownloadState.QUEUED,
                createdAt = Date(),
                updatedAt = Date()
            )
            downloadDao.insert(download)
        } else if (existing.downloadState == DownloadState.FAILED || existing.downloadState == DownloadState.NONE) {
            downloadDao.update(existing.copy(downloadState = DownloadState.QUEUED, updatedAt = Date()))
        }
    }

    override suspend fun queueDownloads(songs: List<Song>) {
        val songIds = songs.map { it.id }
        val existing = downloadDao.getDownloads(songIds).associateBy { it.songId }

        val toInsert = mutableListOf<DownloadData>()
        val toUpdate = mutableListOf<DownloadData>()

        songs.forEach { song ->
            val existingDownload = existing[song.id]
            if (existingDownload == null) {
                toInsert.add(
                    DownloadData(
                        songId = song.id,
                        downloadState = DownloadState.QUEUED,
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                )
            } else if (existingDownload.downloadState == DownloadState.FAILED ||
                       existingDownload.downloadState == DownloadState.NONE) {
                toUpdate.add(
                    existingDownload.copy(downloadState = DownloadState.QUEUED, updatedAt = Date())
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            downloadDao.insertAll(toInsert)
        }
        if (toUpdate.isNotEmpty()) {
            downloadDao.updateAll(toUpdate)
        }
    }

    override suspend fun updateDownloadProgress(
        song: Song,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val existing = downloadDao.getDownload(song.id)
        if (existing != null) {
            downloadDao.update(
                existing.copy(
                    downloadState = DownloadState.DOWNLOADING,
                    downloadProgress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    updatedAt = Date()
                )
            )
        }
    }

    override suspend fun markDownloadCompleted(song: Song, localPath: String, totalBytes: Long) {
        val existing = downloadDao.getDownload(song.id)
        if (existing != null) {
            downloadDao.update(
                existing.copy(
                    downloadState = DownloadState.COMPLETED,
                    localPath = localPath,
                    downloadProgress = 1f,
                    downloadedBytes = totalBytes,
                    totalBytes = totalBytes,
                    downloadedDate = Date(),
                    updatedAt = Date(),
                    errorMessage = null
                )
            )
        }
    }

    override suspend fun markDownloadFailed(song: Song, errorMessage: String) {
        val existing = downloadDao.getDownload(song.id)
        if (existing != null) {
            downloadDao.update(
                existing.copy(
                    downloadState = DownloadState.FAILED,
                    errorMessage = errorMessage,
                    updatedAt = Date()
                )
            )
        }
    }

    override suspend fun pauseDownload(song: Song) {
        downloadDao.updateDownloadState(song.id, DownloadState.PAUSED)
    }

    override suspend fun resumeDownload(song: Song) {
        val existing = downloadDao.getDownload(song.id)
        if (existing != null && existing.downloadState == DownloadState.PAUSED) {
            downloadDao.update(existing.copy(downloadState = DownloadState.QUEUED, updatedAt = Date()))
        }
    }

    override suspend fun cancelDownload(song: Song) {
        downloadDao.deleteBySongId(song.id)
    }

    override suspend fun cancelDownloads(songs: List<Song>) {
        downloadDao.deleteBySongIds(songs.map { it.id })
    }

    override suspend fun removeDownload(song: Song) {
        val download = downloadDao.getDownload(song.id)
        if (download != null) {
            // Delete the file if it exists
            download.localPath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
            downloadDao.delete(download)
        }
    }

    override suspend fun removeDownloads(songs: List<Song>) {
        val downloads = downloadDao.getDownloads(songs.map { it.id })
        downloads.forEach { download ->
            download.localPath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
        }
        downloadDao.deleteBySongIds(songs.map { it.id })
    }

    override suspend fun removeDownloadsByState(state: DownloadState) {
        val downloads = downloadDao.getDownloadsByState(state)
        downloads.forEach { download ->
            download.localPath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
        }
        downloadDao.deleteByState(state)
    }

    override suspend fun removeAllDownloads() {
        val downloads = downloadDao.getAllDownloads()
        downloads.forEach { download ->
            download.localPath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Log but don't fail
                }
            }
        }
        downloadDao.deleteAll()
    }

    override suspend fun getTotalDownloadedSize(): Long {
        return downloadDao.getTotalDownloadedSize() ?: 0L
    }

    override suspend fun getDownloadCountByState(state: DownloadState): Int {
        return downloadDao.getDownloadCountByState(state)
    }

    override fun observeDownloadCountByState(state: DownloadState): Flow<Int> {
        return downloadDao.observeDownloadCountByState(state)
    }

    override suspend fun isDownloaded(song: Song): Boolean {
        val download = downloadDao.getDownload(song.id)
        return download?.downloadState == DownloadState.COMPLETED && download.localPath != null
    }

    override suspend fun getLocalPath(song: Song): String? {
        val download = downloadDao.getDownload(song.id)
        return if (download?.downloadState == DownloadState.COMPLETED) {
            download.localPath
        } else {
            null
        }
    }
}
