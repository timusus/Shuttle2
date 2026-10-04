package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.TranscodeService
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate
import kotlin.concurrent.atomics.update

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
 *
 * A progressive transcode runs on the server as a session named by the play's id, so a seek's re-open replaces it,
 * and [endPlay] stops it (#722): the server otherwise keeps transcoding a skipped song until it times out idle.
 */
@OptIn(ExperimentalAtomicApi::class)
@Inject
class PlexStreamUrlProvider(
    private val authenticationManager: PlexAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap,
    private val streamProfile: StreamProfile,
    private val transcodeService: TranscodeService
) : StreamUrlProvider {
    private val logger = Logger.tagged("PlexStreamUrlProvider")

    /** The plays a progressive transcode was opened for, which [endPlay] stops. */
    private val transcodeSessions = AtomicReference(emptySet<String>())

    override fun handles(scheme: String?): Boolean = scheme == "plex"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String = stream(song, startPositionMs, playId).path

    /**
     * Stops [playId]'s transcode session, if a progressive transcode was opened for it. Best effort: a failure is
     * logged, and the server times the session out as it would have.
     */
    override suspend fun endPlay(playId: String) {
        if (playId !in transcodeSessions.fetchAndUpdate { it - playId }) return
        val address = authenticationManager.getAddress() ?: return
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return
        val result = transcodeService.stop(url = "$address$TRANSCODE_STOP_PATH", token = credentials.accessToken, session = playId)
        if (result is NetworkResult.Failure) logger.warn { "Failed to stop transcode session $playId: ${result.error}" }
    }

    /** @throws IllegalStateException when the server isn't signed in to, or the URL can't be built. */
    fun stream(
        song: Song,
        startPositionMs: Long = 0,
        playId: String? = null
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
            val path = if (playId == null) {
                authenticationManager.buildPlexProgressiveStreamPath(song, authenticatedCredentials, transcodeKbps, startPositionMs)
            } else {
                authenticationManager.buildPlexProgressiveStreamPath(song, authenticatedCredentials, transcodeKbps, startPositionMs, session = playId)
            } ?: throw IllegalStateException("Failed to build plex transcode path")
            if (playId != null) transcodeSessions.update { it + playId }
            PlexStream(path, PROGRESSIVE_TRANSCODE_MIME_TYPE)
        }
    }

    /**
     * The original part file (`song.externalId`) when the player decodes it; otherwise a progressive MP3 transcode at
     * [UNCAPPED_TRANSCODE_KBPS], the same bitrate as an uncapped stream, so the download is one file of real, playable
     * audio recorded under the type it actually is: not the HLS transcode Android streams, whose manifest would be saved
     * as the "file" (#567). The cap doesn't apply: a download keeps the original.
     */
    override fun downloadSource(song: Song): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        return if (isDecodable(song)) {
            val path = authenticationManager.buildPlexPath(song = song, authenticatedCredentials = authenticatedCredentials) ?: return null
            DownloadSource(path, song.mimeType)
        } else {
            val path = authenticationManager.buildPlexProgressiveTranscodePath(song, authenticatedCredentials, UNCAPPED_TRANSCODE_KBPS) ?: return null
            DownloadSource(path, PROGRESSIVE_TRANSCODE_MIME_TYPE)
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

        /** Plex's universal transcoder takes a stop for any media type at its video path. */
        private const val TRANSCODE_STOP_PATH = "/video/:/transcode/universal/stop"
    }
}
