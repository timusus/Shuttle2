package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
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
     * Emby's universal endpoint, serves a part file as it is, so a format the player can't decode (WMA, AIFF) would
     * fail to load and be skipped (#362).
     */
    @Throws(IllegalStateException::class)
    internal fun buildStream(song: Song): PlexStream {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val maxBitrateKbps = streamingBitrateCap.maxBitrateKbps()
        val bitRate = song.bitRate
        val withinCap = maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
        return if (withinCap && isDecodable(song.externalId)) {
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
     * Whether the player decodes the part at [partKey] as it is, going by its container: Plex names a part's file after
     * it (`/library/parts/{id}/{updatedAt}/file.{container}`). The containers are the ones Jellyfin and Emby's
     * universal endpoint is told the player direct-plays (see JellyfinAuthenticationManager.buildJellyfinPath). A part
     * without one plays as it is, as before.
     */
    private fun isDecodable(partKey: String?): Boolean {
        val container = partKey?.substringAfterLast('/')?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        return container.isNullOrEmpty() || container in DECODABLE_CONTAINERS
    }

    // Plex's part-file path (song.externalId) is already the original, untranscoded file, so the
    // download URL is the same one used for streaming without a bitrate cap.
    override suspend fun downloadUri(song: Song): Uri? = buildDownloadPathString(song)?.toUri()

    // Plex has no separate download permission to fall back from: downloadUri is already the
    // only URL there is.
    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = null

    /**
     * String form of [downloadUri]'s path (also used for uncapped streaming, see above), kept separate so
     * tests can assert on it without pulling Robolectric into this module just for `Uri.parse`.
     */
    internal suspend fun buildDownloadPathString(song: Song): String? {
        val authenticatedCredentials = plexAuthenticationManager.getAuthenticatedCredentials() ?: return null
        return plexAuthenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
    }

    private companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"

        /** The bitrate a format the player can't decode is transcoded to when streaming isn't capped. */
        const val UNCAPPED_TRANSCODE_KBPS = 320

        val DECODABLE_CONTAINERS = setOf("mp3", "aac", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "wav", "webm", "weba")
    }
}
