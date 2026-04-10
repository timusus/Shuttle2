package com.simplecityapps.provider.jellyfin

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.shuttle.model.Song
import java.io.File
import javax.inject.Inject

class JellyfinMediaInfoProvider
@Inject
constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val jellyfinTranscodeService: JellyfinTranscodeService,
    private val downloadRepository: DownloadRepository
) : MediaInfoProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "jellyfin"

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
        val jellyfinPath =
            jellyfinAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
                jellyfinAuthenticationManager.buildJellyfinPath(
                    Uri.parse(song.path).pathSegments.last(),
                    authenticatedCredentials
                )?.toUri() ?: run {
                    throw IllegalStateException("Failed to build jellyfin path")
                }
            } ?: run {
                throw IllegalStateException("Failed to authenticate")
            }

        return MediaInfo(
            path = jellyfinPath,
            mimeType = if (castCompatibilityMode) getMimeType(jellyfinPath, song.mimeType) else song.mimeType,
            isRemote = true
        )
    }

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String {
        val response = jellyfinTranscodeService.transcode(path.toString())
        return if (response.isSuccessful) {
            return response.headers()["Content-Type"] ?: defaultMimeType
        } else {
            defaultMimeType
        }
    }
}
