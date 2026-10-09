package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import dev.zacsweers.metro.Inject

/**
 * A Jellyfin song's authenticated stream URL, direct play where the player decodes the file, else a capped transcode in the
 * chosen codec, and its download URL: the original, or a transcode at the download cap when the song is over it.
 */
@Inject
class JellyfinStreamUrlProvider(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val streamingPolicy: StreamingPolicy
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        val maxBitrateKbps = streamingPolicy.maxBitrateKbps()
        val format = streamingPolicy.transcodeFormat()
        val url = authenticationManager.buildJellyfinPath(
            itemId = song.itemId(),
            authenticatedCredentials = authenticatedCredentials,
            maxBitrateKbps = maxBitrateKbps,
            startPositionMs = startPositionMs,
            format = format,
            playId = playId
        ) ?: throw IllegalStateException("Failed to build jellyfin path")
        streamingPolicy.streamOpened(song.path, delivered(song, maxBitrateKbps, format))
        return url
    }

    /**
     * The original file ([JellyfinAuthenticationManager.buildDownloadPath]), recorded as the song's own type, unless the
     * download quality caps it below the song's bitrate (or the bitrate is unknown), or the player can't decode its
     * codec (ALAC): then a progressive transcode (at the cap, if any) in the chosen codec.
     */
    override fun downloadSource(song: Song): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val maxBitrateKbps = streamingPolicy.downloadMaxBitrateKbps()
        val bitRate = song.bitRate
        val overCap = maxBitrateKbps != null && (bitRate == null || bitRate > maxBitrateKbps)
        if (overCap || !authenticationManager.directPlays(song.audioCodec)) {
            return transcodedDownload(song, authenticatedCredentials)
        }
        val url = authenticationManager.buildDownloadPath(song.itemId(), authenticatedCredentials) ?: return null
        return DownloadSource(url, song.mimeType)
    }

    /**
     * A song the player can't decode (ALAC) falls back to the same transcode [downloadSource] gives it: the static
     * stream is the original, which would be saved undecodable. Any other song falls back to the static stream, recorded
     * as its own type. A 403 means the server has revoked download permission, so it's remembered (a transcode isn't
     * the Download endpoint, so one refused says nothing of it); a 401 may only be an expired session.
     */
    override fun downloadFallback(
        song: Song,
        httpStatus: Int
    ): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        if (!authenticationManager.directPlays(song.audioCodec)) return transcodedDownload(song, authenticatedCredentials)
        if (httpStatus == 403) authenticationManager.disableDownloadPermission()
        val url = authenticationManager.buildStreamPath(song.itemId(), authenticatedCredentials) ?: return null
        return DownloadSource(url, song.mimeType)
    }

    /** A progressive transcode in the chosen codec, at the download cap if there is one. */
    private fun transcodedDownload(
        song: Song,
        authenticatedCredentials: AuthenticatedCredentials
    ): DownloadSource? = authenticationManager.buildTranscodedDownloadPath(
        itemId = song.itemId(),
        authenticatedCredentials = authenticatedCredentials,
        maxBitrateKbps = streamingPolicy.downloadMaxBitrateKbps(),
        format = streamingPolicy.transcodeFormat()
    )

    /**
     * What the server sends: the universal endpoint decides, so this is the decision it makes for a song whose bitrate is
     * known to be over the cap, or whose codec the player can't decode as it is: a transcode (at the cap, if any) in the
     * chosen codec. A decodable song within the cap, or of unknown bitrate, is taken to play as the original file.
     */
    private fun delivered(
        song: Song,
        maxBitrateKbps: Int?,
        format: TranscodeFormat
    ): DeliveredFormat? {
        val bitRate = song.bitRate
        val overCap = maxBitrateKbps != null && bitRate != null && bitRate > maxBitrateKbps
        if (!overCap && authenticationManager.directPlays(song.audioCodec)) return null
        return authenticationManager.streamTarget(format).codec.delivered(maxBitrateKbps)
    }

    private fun Song.itemId(): String = path.substringAfterLast('/')
}
