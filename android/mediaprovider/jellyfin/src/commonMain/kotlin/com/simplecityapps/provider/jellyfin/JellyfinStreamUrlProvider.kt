package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.DownloadSource
import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A Jellyfin song's authenticated stream URL, direct play where the player decodes the file, else a capped transcode, and its download URL. */
@Inject
class JellyfinStreamUrlProvider(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long,
        playId: String?
    ): String {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        return authenticationManager.buildJellyfinPath(
            itemId = song.path.substringAfterLast('/'),
            authenticatedCredentials = authenticatedCredentials,
            maxBitrateKbps = streamingBitrateCap.maxBitrateKbps(),
            startPositionMs = startPositionMs
        ) ?: throw IllegalStateException("Failed to build jellyfin path")
    }

    /** The original file ([JellyfinAuthenticationManager.buildDownloadPath]), recorded as the song's own type. */
    override fun downloadSource(song: Song): DownloadSource? {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val url = authenticationManager.buildDownloadPath(song.path.substringAfterLast('/'), authenticatedCredentials) ?: return null
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
}
