package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A Plex song's stream for Media3 and its download, both [PlexStreamUrlProvider]'s, with their MIME types. */
class PlexMediaInfoProvider
@Inject
constructor(
    private val streamUrls: PlexStreamUrlProvider
) : MediaInfoProvider {
    override fun handles(scheme: String?): Boolean = streamUrls.handles(scheme)

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val stream = streamUrls.stream(song)
        return MediaInfo(
            path = stream.path.toUri(),
            mimeType = stream.mimeType,
            isRemote = true
        )
    }

    override suspend fun downloadInfo(song: Song): DownloadInfo? = streamUrls.downloadSource(song)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    // Plex has no separate download permission to fall back from: downloadInfo's URI is already the
    // only one there is.
    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = null
}
