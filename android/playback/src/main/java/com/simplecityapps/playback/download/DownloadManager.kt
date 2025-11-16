package com.simplecityapps.playback.download

import android.content.Context
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.provider.plex.PlexAuthenticationManager
import com.simplecityapps.shuttle.model.DownloadState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            // Get the song from the download repository
            val download = downloadRepository.getDownload(
                // We need to create a minimal Song object with just the ID
                // In a real implementation, we'd fetch the full Song from SongRepository
                Song(
                    id = songId,
                    name = null,
                    albumArtist = null,
                    artists = emptyList(),
                    album = null,
                    track = null,
                    disc = null,
                    duration = 0,
                    date = null,
                    genres = emptyList(),
                    path = "",
                    size = 0,
                    mimeType = "",
                    lastModified = null,
                    lastPlayed = null,
                    lastCompleted = null,
                    playCount = 0,
                    playbackPosition = 0,
                    blacklisted = false,
                    mediaProvider = MediaProviderType.Shuttle,
                    lyrics = null,
                    grouping = null,
                    bitRate = null,
                    bitDepth = null,
                    sampleRate = null,
                    channelCount = null
                )
            ) ?: return

            // TODO: Get full song from SongRepository
            // For now, this is a placeholder
            Timber.d("Download started for song ID: $songId")

        } catch (e: Exception) {
            Timber.e(e, "Download failed for song ID: $songId")
            val song = Song(
                id = songId,
                name = null,
                albumArtist = null,
                artists = emptyList(),
                album = null,
                track = null,
                disc = null,
                duration = 0,
                date = null,
                genres = emptyList(),
                path = "",
                size = 0,
                mimeType = "",
                lastModified = null,
                lastPlayed = null,
                lastCompleted = null,
                playCount = 0,
                playbackPosition = 0,
                blacklisted = false,
                mediaProvider = MediaProviderType.Shuttle,
                lyrics = null,
                grouping = null,
                bitRate = null,
                bitDepth = null,
                sampleRate = null,
                channelCount = null
            )
            downloadRepository.markDownloadFailed(song, e.message ?: "Unknown error")
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
