package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.shuttle.model.Song
import java.io.File
import javax.inject.Inject

class PlexMediaInfoProvider
@Inject
constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager,
    private val downloadRepository: DownloadRepository
) : MediaInfoProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "plex"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        // Check if the song is downloaded for offline playback
        val localPath = downloadRepository.getLocalPath(song)
        if (localPath != null && File(localPath).exists()) {
            return MediaInfo(
                path = File(localPath).toUri(),
                mimeType = song.mimeType,
                isRemote = false
            )
        }

        // Fall back to streaming
        val plexPath =
            plexAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
                plexAuthenticationManager.buildPlexPath(
                    song = song,
                    authenticatedCredentials = authenticatedCredentials
                )?.toUri() ?: run {
                    throw IllegalStateException("Failed to build plex path")
                }
            } ?: run {
                throw IllegalStateException("Failed to authenticate")
            }

        return MediaInfo(
            path = plexPath,
            mimeType = song.mimeType,
            isRemote = true
        )
    }
}
