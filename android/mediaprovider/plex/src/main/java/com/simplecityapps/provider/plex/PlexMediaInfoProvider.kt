package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.DirectPlayFormats
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class PlexMediaInfoProvider
@Inject
constructor(
    private val plexAuthenticationManager: PlexAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap
) : MediaInfoProvider {
    override fun handles(scheme: String?): Boolean = scheme == "plex"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val stream = buildStream(song)
        return MediaInfo(
            path = stream.path.toUri(),
            mimeType = stream.mimeType,
            isRemote = true
        )
    }

    /** A stream URL and the MIME type it serves. */
    internal data class PlexStream(
        val path: String,
        val mimeType: String
    )

    /**
     * [getMediaInfo]'s stream, kept separate so tests can assert on it without pulling Robolectric into this module
     * for `Uri.parse`. The original part file when it's a format the player decodes and there's no
     * [StreamingBitrateCap] or the song's bitrate is known to be within it; otherwise an HLS transcode at the cap, or at
     * [UNCAPPED_TRANSCODE_KBPS] without one. An unknown bitrate transcodes, as Jellyfin does. Plex, unlike Jellyfin and
     * Emby's universal endpoint, serves a part file as it is, so a format the player can't decode (WMA, AIFF, or ALAC
     * inside an otherwise-playable container) would fail to load and be skipped (#362, #567).
     */
    @Throws(IllegalStateException::class)
    internal fun buildStream(song: Song): PlexStream {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val maxBitrateKbps = streamingBitrateCap.maxBitrateKbps()
        val bitRate = song.bitRate
        val withinCap = maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
        return if (withinCap && isDecodable(song.externalId, song.audioCodec)) {
            val path = plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
                ?: throw IllegalStateException("Failed to build plex path")
            PlexStream(path, song.mimeType)
        } else {
            val path = plexAuthenticationManager.buildPlexTranscodePath(song, authenticatedCredentials, maxBitrateKbps ?: UNCAPPED_TRANSCODE_KBPS)
                ?: throw IllegalStateException("Failed to build plex transcode path")
            PlexStream(path, HLS_MIME_TYPE)
        }
    }

    /**
     * Whether the player decodes the part at [partKey] as it is. The container comes from the part's file extension
     * (Plex names a part's file after it: `/library/parts/{id}/{updatedAt}/file.{container}`).
     */
    private fun isDecodable(
        partKey: String?,
        audioCodec: String?
    ): Boolean = DirectPlayFormats.isDecodable(
        container = partKey?.substringAfterLast('/')?.substringAfterLast('.', missingDelimiterValue = ""),
        audioCodec = audioCodec
    )

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
     * at [UNCAPPED_TRANSCODE_KBPS], same bitrate as an uncapped stream — not the HLS transcode streaming uses, since
     * an HLS manifest URL downloaded as one "file" saves the manifest text rather than the audio (#567).
     */
    internal suspend fun buildDownloadStream(song: Song): PlexStream? {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials() ?: return null
        return if (isDecodable(song.externalId, song.audioCodec)) {
            val path = plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials) ?: return null
            PlexStream(path, song.mimeType)
        } else {
            val path = plexAuthenticationManager.buildPlexProgressiveTranscodePath(song, authenticatedCredentials, UNCAPPED_TRANSCODE_KBPS) ?: return null
            PlexStream(path, PROGRESSIVE_TRANSCODE_MIME_TYPE)
        }
    }

    private companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"
        const val PROGRESSIVE_TRANSCODE_MIME_TYPE = "audio/mpeg"

        /** The bitrate a format the player can't decode is transcoded to when streaming isn't capped. */
        const val UNCAPPED_TRANSCODE_KBPS = 320
    }
}
