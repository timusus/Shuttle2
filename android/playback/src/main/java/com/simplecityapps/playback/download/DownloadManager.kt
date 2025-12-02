package com.simplecityapps.playback.download

import android.content.Context
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Manages downloading of songs for offline playback
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val songRepository: SongRepository,
    private val jellyfinAuthManager: JellyfinAuthenticationManager,
    private val embyAuthManager: EmbyAuthenticationManager,
    private val plexAuthManager: PlexAuthenticationManager,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val downloadJobs = mutableMapOf<Long, Job>()
    private val mutex = Mutex()

    private val _downloadingCount = MutableStateFlow(0)
    val downloadingCount: StateFlow<Int> = _downloadingCount

    /**
     * Queue a song for download
     */
    suspend fun queueDownload(song: Song) {
        if (!song.mediaProvider.remote) {
            Timber.w("Cannot download local song: ${song.name}")
            return
        }

        downloadRepository.queueDownload(song)
        processNextDownload()
    }

    /**
     * Queue multiple songs for download
     */
    suspend fun queueDownloads(songs: List<Song>) {
        val remoteSongs = songs.filter { it.mediaProvider.remote }
        if (remoteSongs.isEmpty()) {
            Timber.w("No remote songs to download")
            return
        }

        downloadRepository.queueDownloads(remoteSongs)
        processNextDownload()
    }

    /**
     * Cancel a download
     */
    suspend fun cancelDownload(song: Song) {
        mutex.withLock {
            downloadJobs[song.id]?.cancel()
            downloadJobs.remove(song.id)
        }
        downloadRepository.cancelDownload(song)
        updateDownloadingCount()
        processNextDownload()
    }

    /**
     * Cancel multiple downloads
     */
    suspend fun cancelDownloads(songs: List<Song>) {
        mutex.withLock {
            songs.forEach { song ->
                downloadJobs[song.id]?.cancel()
                downloadJobs.remove(song.id)
            }
        }
        downloadRepository.cancelDownloads(songs)
        updateDownloadingCount()
        processNextDownload()
    }

    /**
     * Pause a download
     */
    suspend fun pauseDownload(song: Song) {
        mutex.withLock {
            downloadJobs[song.id]?.cancel()
            downloadJobs.remove(song.id)
        }
        downloadRepository.pauseDownload(song)
        updateDownloadingCount()
        processNextDownload()
    }

    /**
     * Resume a paused download
     */
    suspend fun resumeDownload(song: Song) {
        downloadRepository.resumeDownload(song)
        processNextDownload()
    }

    /**
     * Remove a downloaded song (delete file and database entry)
     */
    suspend fun removeDownload(song: Song) {
        downloadRepository.removeDownload(song)
    }

    /**
     * Remove multiple downloaded songs
     */
    suspend fun removeDownloads(songs: List<Song>) {
        downloadRepository.removeDownloads(songs)
    }

    /**
     * Process the next queued download
     */
    private suspend fun processNextDownload() {
        mutex.withLock {
            // Limit concurrent downloads to 3
            if (downloadJobs.size >= 3) {
                return
            }

            val queuedDownloads = downloadRepository.getDownloadsByState(DownloadState.QUEUED)
            if (queuedDownloads.isEmpty()) {
                return
            }

            val nextDownload = queuedDownloads.firstOrNull() ?: return

            val job = scope.launch {
                performDownload(nextDownload.songId)
            }

            downloadJobs[nextDownload.songId] = job
            updateDownloadingCount()

            job.invokeOnCompletion {
                scope.launch {
                    mutex.withLock {
                        downloadJobs.remove(nextDownload.songId)
                        updateDownloadingCount()
                    }
                    processNextDownload()
                }
            }
        }
    }

    /**
     * Perform the actual download of a song
     */
    private suspend fun performDownload(songId: Long) {
        try {
            // Get the full song from the repository
            val songs = songRepository.getSongs(com.simplecityapps.mediaprovider.repository.songs.SongQuery.All()).firstOrNull()
            val song = songs?.find { it.id == songId }

            if (song == null) {
                Timber.e("Song not found for download: $songId")
                return
            }

            Timber.d("Download started for song: ${song.name}")

            // Get download URL
            val downloadUrl = getDownloadUrl(song)
            if (downloadUrl == null) {
                Timber.e("Failed to get download URL for song: ${song.name}")
                downloadRepository.markDownloadFailed(song, "Failed to generate download URL")
                return
            }

            // Get destination file
            val destinationFile = getDownloadPath(song)

            // Create parent directories
            destinationFile.parentFile?.mkdirs()

            // Download the file
            withContext(Dispatchers.IO) {
                downloadFile(downloadUrl, destinationFile) { progress, downloadedBytes, totalBytes ->
                    scope.launch {
                        downloadRepository.updateDownloadProgress(
                            song,
                            progress,
                            downloadedBytes,
                            totalBytes
                        )
                    }
                }
            }

            // Mark as completed
            downloadRepository.markDownloadCompleted(
                song,
                destinationFile.absolutePath,
                destinationFile.length()
            )

            Timber.d("Download completed for song: ${song.name}")

        } catch (e: Exception) {
            Timber.e(e, "Download failed for song ID: $songId")

            // Try to get the song to mark it as failed
            val songs = songRepository.getSongs(com.simplecityapps.mediaprovider.repository.songs.SongQuery.All()).firstOrNull()
            val song = songs?.find { it.id == songId }

            if (song != null) {
                downloadRepository.markDownloadFailed(song, e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Get download URL for a song based on provider
     */
    private fun getDownloadUrl(song: Song): String? {
        return when (song.mediaProvider) {
            MediaProviderType.Jellyfin -> {
                val credentials = jellyfinAuthManager.getAuthenticatedCredentials() ?: return null
                val itemId = song.externalId ?: return null
                jellyfinAuthManager.buildJellyfinDownloadPath(itemId, credentials)
            }
            MediaProviderType.Emby -> {
                val credentials = embyAuthManager.getAuthenticatedCredentials() ?: return null
                val itemId = song.externalId ?: return null
                embyAuthManager.buildEmbyDownloadPath(itemId, credentials)
            }
            MediaProviderType.Plex -> {
                val credentials = plexAuthManager.getAuthenticatedCredentials() ?: return null
                plexAuthManager.buildPlexDownloadPath(song, credentials)
            }
            else -> null
        }
    }

    /**
     * Get the local file path for a download
     */
    private fun getDownloadPath(song: Song): File {
        val downloadsDir = File(context.filesDir, "downloads/${song.mediaProvider.name.lowercase()}")
        downloadsDir.mkdirs()

        val extension = song.mimeType.substringAfter("/").let {
            when (it) {
                "mpeg" -> "mp3"
                "mp4" -> "m4a"
                else -> it
            }
        }

        return File(downloadsDir, "${song.externalId}.$extension")
    }

    /**
     * Download a file from URL to local storage
     */
    private suspend fun downloadFile(url: String, destination: File, onProgress: (Float, Long, Long) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = if (contentLength > 0) {
                            totalBytesRead.toFloat() / contentLength.toFloat()
                        } else {
                            0f
                        }

                        onProgress(progress, totalBytesRead, contentLength)
                    }
                }
            }
        }
    }

    private suspend fun updateDownloadingCount() {
        _downloadingCount.value = downloadJobs.size
    }
}
