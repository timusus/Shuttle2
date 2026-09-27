package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.forPath
import com.simplecityapps.shuttle.model.Song

/**
 * What the engine opens for a song: a server song's authenticated stream URL from the provider that handles its path
 * (`jellyfin://item/...`), a file path as a `file://` URL, and anything else as it is. A server stream opens at a
 * position: its URL carries the start as `StartTimeTicks`, which a transcode starts from and direct play ignores.
 *
 * Unity gain for now: ReplayGain on iOS is still to come. A provider that can't build a URL (signed out, no address)
 * throws, which fails the song as the controller expects.
 */
class SongStreamResolver(
    private val streamUrls: Collection<StreamUrlProvider>
) : IosStreamResolver {
    override suspend fun resolve(
        song: Song,
        startPositionMs: Long
    ): IosStream {
        val provider = streamUrls.forPath(song.path)
        return when {
            provider != null -> IosStream(url = provider.streamUrl(song, startPositionMs), opensAtPosition = true)
            song.path.startsWith("/") -> IosStream(url = fileUrl(song.path))
            else -> IosStream(url = song.path)
        }
    }

    /** The engine parses the URL, so the path's spaces and reserved characters are escaped, each segment on its own. */
    private fun fileUrl(path: String): String = "file://" + path.split('/').joinToString("/") { it.percentEncoded() }

    private fun String.percentEncoded(): String = buildString {
        for (byte in this@percentEncoded.encodeToByteArray()) {
            val char = byte.toInt().toChar()
            if (byte >= 0 && (char.isLetterOrDigit() || char in UNRESERVED)) {
                append(char)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    private companion object {
        const val UNRESERVED = "-._~"
        const val HEX = "0123456789ABCDEF"
    }
}
