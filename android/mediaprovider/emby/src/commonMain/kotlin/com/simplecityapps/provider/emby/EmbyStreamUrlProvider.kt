package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** A Emby song's authenticated stream URL, direct play where the player decodes the file, else a capped transcode. */
@Inject
class EmbyStreamUrlProvider(
    private val authenticationManager: EmbyAuthenticationManager,
    private val streamingBitrateCap: StreamingBitrateCap
) : StreamUrlProvider {
    override fun handles(scheme: String?): Boolean = scheme == "emby"

    override fun streamUrl(
        song: Song,
        startPositionMs: Long
    ): String {
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        return authenticationManager.buildEmbyPath(
            itemId = song.path.substringAfterLast('/'),
            authenticatedCredentials = authenticatedCredentials,
            maxBitrateKbps = streamingBitrateCap.maxBitrateKbps(),
            startPositionMs = startPositionMs
        ) ?: throw IllegalStateException("Failed to build emby path")
    }
}
