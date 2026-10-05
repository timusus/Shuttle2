package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * An Emby song's authenticated stream URL, direct play where the player decodes the file, else a capped transcode in the
 * chosen codec, and its download URL: the original, or a transcode at the download cap when the song is over it.
 */
@Inject
class EmbyStreamUrlProvider(
    private val authenticationManager: EmbyAuthenticationManager,
    private val streamingPolicy: StreamingPolicy
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "emby"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        return authenticationManager.buildEmbyPath(
            itemId = song.itemId(),
            authenticatedCredentials = authenticatedCredentials,
            maxBitrateKbps = streamingPolicy.maxBitrateKbps(),
            startPositionMs = startPositionMs,
            format = streamingPolicy.transcodeFormat()
        ) ?: throw IllegalStateException("Failed to build emby path")
    }

    /**
     * The original file ([EmbyAuthenticationManager.buildDownloadPath]), recorded as the song's own type, unless the
     * download quality caps it below the song's bitrate (or the bitrate is unknown): then a progressive transcode at
     * the cap in the chosen codec.
     */
    override fun downloadSource(song: Song): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val maxBitrateKbps = streamingPolicy.downloadMaxBitrateKbps()
        val bitRate = song.bitRate
        if (maxBitrateKbps != null && (bitRate == null || bitRate > maxBitrateKbps)) {
            return authenticationManager.buildTranscodedDownloadPath(
                itemId = song.itemId(),
                authenticatedCredentials = authenticatedCredentials,
                maxBitrateKbps = maxBitrateKbps,
                format = streamingPolicy.transcodeFormat()
            )
        }
        val url = authenticationManager.buildDownloadPath(song.itemId(), authenticatedCredentials) ?: return null
        return DownloadSource(url, song.mimeType)
    }

    /** A 403 means the server has revoked download permission, so it's remembered; a 401 may only be an expired session. */
    override fun downloadFallback(
        song: Song,
        httpStatus: Int
    ): DownloadSource? {
        if (httpStatus == 403) authenticationManager.disableDownloadPermission()
        return super.downloadFallback(song, httpStatus)
    }

    private fun Song.itemId(): String = path.substringAfterLast('/')
}
