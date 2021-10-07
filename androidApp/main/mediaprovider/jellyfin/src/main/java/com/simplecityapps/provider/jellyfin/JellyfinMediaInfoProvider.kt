package com.simplecityapps.provider.jellyfin

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class JellyfinMediaInfoProvider @Inject constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val jellyfinTranscodeService: JellyfinTranscodeService
) : MediaInfoProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "jellyfin"
    }

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(song: Song, castCompatibilityMode: Boolean): MediaInfo {
        val jellyfinPath = jellyfinAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
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
            mimeType = if (castCompatibilityMode) getMimeType(jellyfinPath, song.mimeType ?: "audio/*") else song.mimeType ?: "audio/*",
            isRemote = true
        )
    }

    private suspend fun getMimeType(path: Uri, defaultMimeType: String): String {
        val response = jellyfinTranscodeService.transcode(path.toString())
        return if (response.isSuccessful) {
            return response.headers()["Content-Type"] ?: defaultMimeType
        } else {
            defaultMimeType
        }
    }
}