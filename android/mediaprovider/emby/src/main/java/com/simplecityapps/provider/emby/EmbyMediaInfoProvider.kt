package com.simplecityapps.provider.emby

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.repository.downloads.DownloadRepository
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.shuttle.model.Song
import java.io.File
import javax.inject.Inject

class EmbyMediaInfoProvider
@Inject
constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val embyTranscodeService: EmbyTranscodeService,
    private val downloadRepository: DownloadRepository
) : MediaInfoProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "emby"

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
        val embyPath =
            embyAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
                embyAuthenticationManager.buildEmbyPath(
                    Uri.parse(song.path).pathSegments.last(),
                    authenticatedCredentials
                )?.toUri() ?: run {
                    throw IllegalStateException("Failed to build emby path")
                }
            } ?: run {
                throw IllegalStateException("Failed to authenticate")
            }

        return MediaInfo(
            path = embyPath,
            mimeType = if (castCompatibilityMode) getMimeType(embyPath, song.mimeType) else song.mimeType,
            isRemote = true
        )
    }

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String {
        val response = embyTranscodeService.transcode(path.toString())
        return if (response.isSuccessful) {
            return response.headers()["Content-Type"] ?: defaultMimeType
        } else {
            defaultMimeType
        }
    }
}
