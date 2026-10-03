package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A stream URL and the MIME type it serves. */
data class PlexStream(
    val path: String,
    val mimeType: String
)

/**
 * A Plex song's authenticated stream: the original part file when it's a format this platform's player decodes
 * ([StreamProfile.directPlayFormats]) and there's no [StreamingBitrateCap] or the song's bitrate is known to be within
 * it; otherwise a transcode at the cap, or at [UNCAPPED_TRANSCODE_KBPS] without one. An unknown bitrate transcodes, as
 * Jellyfin does. Plex, unlike Jellyfin and Emby's universal endpoint, serves a part file as it is, so a format the
 * player can't decode (WMA, or ALAC inside an otherwise-playable container on Android) would fail to load and be
 * skipped (#362, #567).
 *
 * The transcode follows [StreamProfile.transcodingProtocol]: an HLS stream of AAC where the player plays HLS
 * (Android's Media3), which stays seekable; otherwise one progressive MP3 (iOS's engine), which starts at the position
 * it's asked for.
 */
@Inject
class PlexStreamUrlProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap,
    private val streamProfile: StreamProfile
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "plex"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long
    ): String = stream(song, startPositionMs).path

    /** @throws IllegalStateException when the server isn't signed in to, or the URL can't be built. */
    fun stream(
        song: Song,
        startPositionMs: Long = 0
    ): PlexStream {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val maxBitrateKbps = streamingBitrateCap.maxBitrateKbps()
        val bitRate = song.bitRate
        val withinCap = maxBitrateKbps == null || (bitRate != null && bitRate <= maxBitrateKbps)
        if (withinCap && isDecodable(song)) {
            val path = authenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials)
                ?: throw IllegalStateException("Failed to build plex path")
            return PlexStream(path, song.mimeType)
        }
        val transcodeKbps = maxBitrateKbps ?: UNCAPPED_TRANSCODE_KBPS
        return if (streamProfile.transcodingProtocol == HLS) {
            val path = authenticationManager.buildPlexTranscodePath(song, authenticatedCredentials, transcodeKbps)
                ?: throw IllegalStateException("Failed to build plex transcode path")
            PlexStream(path, HLS_MIME_TYPE)
        } else {
            val path = authenticationManager.buildPlexProgressiveStreamPath(song, authenticatedCredentials, transcodeKbps, startPositionMs)
                ?: throw IllegalStateException("Failed to build plex transcode path")
            PlexStream(path, PROGRESSIVE_TRANSCODE_MIME_TYPE)
        }
    }

    /**
     * Whether the player decodes [song]'s part as it is. The container comes from the part's file extension (Plex
     * names a part's file after it: `/library/parts/{id}/{updatedAt}/file.{container}`).
     */
    fun isDecodable(song: Song): Boolean = streamProfile.directPlayFormats.isDecodable(
        container = song.externalId?.substringAfterLast('/')?.substringAfterLast('.', missingDelimiterValue = ""),
        audioCodec = song.audioCodec
    )

    companion object {
        const val HLS_MIME_TYPE = "application/x-mpegURL"
        const val PROGRESSIVE_TRANSCODE_MIME_TYPE = "audio/mpeg"

        /** The bitrate a format the player can't decode is transcoded to when streaming isn't capped. */
        const val UNCAPPED_TRANSCODE_KBPS = 320

        private const val HLS = "hls"
    }
}
