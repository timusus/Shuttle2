package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.provider.plex.http.PlexTranscodeService
import com.simplecityapps.shuttle.model.Song
import timber.log.Timber
import javax.inject.Inject

class PlexMediaInfoProvider @Inject constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager,
    private val plexTranscodeService: PlexTranscodeService
) : MediaInfoProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "plex"
    }

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(song: Song): MediaInfo {
        val plexPath = plexAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
            plexAuthenticationManager.buildPlexPath(
                Uri.parse(song.path),
                authenticatedCredentials
            )?.toUri() ?: run {
                throw IllegalStateException("Failed to build plex path")
            }
        } ?: run {
            throw IllegalStateException("Failed to authenticate")
        }

        val mimeType = getMimeType(plexPath, song.mimeType)

        Timber.i("mimeType: $mimeType")

        return MediaInfo(path = plexPath, mimeType = mimeType, isRemote = true)
    }

    private suspend fun getMimeType(path: Uri, defaultMimeType: String): String {
        val response = plexTranscodeService.transcode(path.toString())
        return if (response.isSuccessful) {
            var contentType = response.headers().get("Content-Type") ?: defaultMimeType
            if (contentType == "application/vnd.apple.mpegurl") {
                contentType = "application/x-mpegURL"
            }
            Timber.i("Returning mimetype: $contentType")

            return contentType
        } else {
            Timber.i("Returning default mimetype ($defaultMimeType)")
            defaultMimeType
        }
    }
}