package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A Plex song's stream for Media3 ([PlexStreamUrlProvider]'s, with its MIME type) and its download. */
class PlexMediaInfoProvider
@Inject
constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager,
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

    // Plex's part-file path (song.externalId) is the original, untranscoded file, used for download when the player
    // can decode it; otherwise a progressive (single-file) transcode, so Media3's downloader saves real, playable
    // audio under the mime type it's actually saving (#567).
    override suspend fun downloadInfo(song: Song): DownloadInfo? = buildDownloadStream(song)?.let { stream -> DownloadInfo(stream.path.toUri(), stream.mimeType) }

    // Plex has no separate download permission to fall back from: downloadInfo's URI is already the
    // only one there is.
    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = null

    /**
     * [downloadInfo]'s stream, kept separate so tests can assert on it without pulling Robolectric into this module
     * for `Uri.parse`. The original part file when the player can decode it; otherwise a progressive MP3 transcode
     * at [PlexStreamUrlProvider.UNCAPPED_TRANSCODE_KBPS], same bitrate as an uncapped stream — not the HLS transcode
     * streaming uses, since an HLS manifest URL downloaded as one "file" saves the manifest text rather than the
     * audio (#567).
     */
    internal suspend fun buildDownloadStream(song: Song): PlexStream? {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials() ?: return null
        return if (streamUrls.isDecodable(song)) {
            val path = plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials) ?: return null
            PlexStream(path, song.mimeType)
        } else {
            val path = plexAuthenticationManager.buildPlexProgressiveTranscodePath(song, authenticatedCredentials, PlexStreamUrlProvider.UNCAPPED_TRANSCODE_KBPS) ?: return null
            PlexStream(path, PlexStreamUrlProvider.PROGRESSIVE_TRANSCODE_MIME_TYPE)
        }
    }
}
