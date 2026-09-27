package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A Jellyfin song's authenticated stream URL, direct play where the player decodes the file, else a capped transcode. */
@Inject
class JellyfinStreamUrlProvider(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long
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
}
