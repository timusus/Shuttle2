package com.simplecityapps.playback.download

import android.content.Context
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case for managing song downloads
 */
class DownloadUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val downloadRepository: DownloadRepository
) {

    /**
     * Queue a song for download
     */
    suspend fun downloadSong(song: Song) {
        downloadManager.queueDownload(song)
        DownloadService.startDownload(context, listOf(song))
    }

    /**
     * Queue multiple songs for download
     */
    suspend fun downloadSongs(songs: List<Song>) {
        downloadManager.queueDownloads(songs)
        DownloadService.startDownload(context, songs)
    }

    /**
     * Download all songs from an album
     */
    suspend fun downloadAlbum(album: Album, songs: List<Song>) {
        val albumSongs = songs.filter { song ->
            song.album == album.name && song.albumArtist == album.albumArtist
        }
        downloadSongs(albumSongs)
    }

    /**
     * Download all songs from a playlist
     */
    suspend fun downloadPlaylist(playlist: Playlist, songs: List<Song>) {
        downloadSongs(songs)
    }

    /**
     * Cancel a song download
     */
    suspend fun cancelDownload(song: Song) {
        downloadManager.cancelDownload(song)
    }

    /**
     * Cancel multiple downloads
     */
    suspend fun cancelDownloads(songs: List<Song>) {
        downloadManager.cancelDownloads(songs)
    }

    /**
     * Pause a download
     */
    suspend fun pauseDownload(song: Song) {
        downloadManager.pauseDownload(song)
        DownloadService.pauseDownload(context, song)
    }

    /**
     * Resume a paused download
     */
    suspend fun resumeDownload(song: Song) {
        downloadManager.resumeDownload(song)
        DownloadService.startDownload(context, listOf(song))
    }

    /**
     * Remove a downloaded song (delete file and database entry)
     */
    suspend fun removeDownload(song: Song) {
        downloadManager.removeDownload(song)
    }

    /**
     * Remove multiple downloaded songs
     */
    suspend fun removeDownloads(songs: List<Song>) {
        downloadManager.removeDownloads(songs)
    }

    /**
     * Check if a song is downloaded
     */
    suspend fun isDownloaded(song: Song): Boolean {
        return downloadRepository.isDownloaded(song)
    }

    /**
     * Get download state for a song
     */
    fun observeDownloadState(song: Song): Flow<DownloadState> {
        return downloadRepository.observeDownload(song).map { download ->
            download?.downloadState ?: DownloadState.NONE
        }
    }

    /**
     * Get download progress for a song
     */
    fun observeDownloadProgress(song: Song): Flow<Float> {
        return downloadRepository.observeDownload(song).map { download ->
            download?.downloadProgress ?: 0f
        }
    }

    /**
     * Get all downloads
     */
    fun observeAllDownloads() = downloadRepository.observeAllDownloads()

    /**
     * Get downloaded count
     */
    fun observeDownloadedCount(): Flow<Int> {
        return downloadRepository.observeDownloadCountByState(DownloadState.COMPLETED)
    }

    /**
     * Get total downloaded size
     */
    suspend fun getTotalDownloadedSize(): Long {
        return downloadRepository.getTotalDownloadedSize()
    }

    /**
     * Remove all downloads
     */
    suspend fun removeAllDownloads() {
        downloadRepository.removeAllDownloads()
    }
}
