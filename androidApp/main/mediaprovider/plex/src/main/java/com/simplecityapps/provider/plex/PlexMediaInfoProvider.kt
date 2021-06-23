package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class PlexMediaInfoProvider @Inject constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager
) : MediaInfoProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "plex"
    }

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(song: Song, castCompatibilityMode: Boolean): MediaInfo {
        val plexPath = plexAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
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