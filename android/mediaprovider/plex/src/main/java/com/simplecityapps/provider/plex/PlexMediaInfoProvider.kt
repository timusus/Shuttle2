package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class PlexMediaInfoProvider
@Inject
constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager
) : MediaInfoProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "plex"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val plexPathString = plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
            ?: throw IllegalStateException("Failed to build plex path")

        return MediaInfo(
            path = plexPathString.toUri(),
            mimeType = song.mimeType,
            isRemote = true
        )
    }

    // Plex's part-file path (song.externalId) is already the original, untranscoded file, so the
    // download URL is the same one used for streaming.
    override suspend fun downloadUri(song: Song): Uri? = buildDownloadPathString(song)?.toUri()

    // Plex has no separate download permission to fall back from: downloadUri is already the
    // only URL there is.
    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = null

    /**
     * String form of [downloadUri]'s path (also used for streaming, see above), kept separate so
     * tests can assert on it without pulling Robolectric into this module just for `Uri.parse`.
     */
    internal suspend fun buildDownloadPathString(song: Song): String? {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials() ?: return null
        return plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
    }
}
