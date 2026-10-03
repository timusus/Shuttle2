package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.forPath
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.dsp.replaygain.replayGain
import com.simplecityapps.playback.dsp.replaygain.replayGainDb
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.model.Song

/**
 * What the engine opens for a song: a server song's authenticated stream URL from the provider that handles its path
 * (`jellyfin://item/...`), a file path as a `file://` URL, and anything else as it is. A server stream opens at a
 * position: its URL carries the start as `StartTimeTicks`, which a transcode starts from and direct play ignores.
 *
 * Each stream carries the song's ReplayGain under the user's [replayGainMode] and [preAmpGainDb], by the rule Android's
 * `ReplayGainAudioProcessor` applies ([replayGainDb]); the engine's limiter keeps a boost from clipping. Both are read
 * as each song is resolved, so a change applies from the next song. A provider that can't build a URL (signed out, no
 * address) throws, which fails the song as the controller expects, and so does a server song that
 * [serverStreamAccess] refuses (streaming needs Shuttle Music Pro or the trial), so the controller skips it. One it
 * can't decide yet (StoreKit hasn't answered) throws [ServerStreamNotAllowedException] marked
 * [ServerStreamNotAllowedException.undecided], which the controller doesn't hold against the song.
 */
class SongStreamResolver(
    private val streamUrls: Collection<StreamUrlProvider>,
    private val replayGainMode: () -> ReplayGainMode,
    private val preAmpGainDb: () -> Float,
    private val serverStreamAccess: suspend (song: Song, playRequested: Boolean) -> ServerAccess = { _, _ -> ServerAccess.Allowed }
) : IosStreamResolver {
    override suspend fun resolve(
        song: Song,
        startPositionMs: Long,
        playRequested: Boolean
    ): IosStream {
        val provider = streamUrls.forPath(song.path)
        val gainDb = replayGainDb(replayGainMode(), preAmpGainDb().toDouble(), song.replayGain).toFloat()
        if (provider != null) {
            when (serverStreamAccess(song, playRequested)) {
                ServerAccess.Allowed -> Unit
                ServerAccess.Refused -> throw ServerStreamNotAllowedException(song, undecided = false)
                ServerAccess.Undecided -> throw ServerStreamNotAllowedException(song, undecided = true)
            }
        }
        return when {
            provider != null -> IosStream(url = provider.streamUrl(song, startPositionMs), gainDb = gainDb, opensAtPosition = true)
            song.path.startsWith("/") -> IosStream(url = fileUrl(song.path), gainDb = gainDb)
            else -> IosStream(url = song.path, gainDb = gainDb)
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

/**
 * A server song refused because streaming it needs Shuttle Music Pro or the trial, or, if [undecided], because StoreKit
 * hadn't said yet whether the user has them.
 */
class ServerStreamNotAllowedException(
    song: Song,
    val undecided: Boolean
) : IllegalStateException(
    if (undecided) "Streaming ${song.name} waits on the App Store's answer" else "Streaming ${song.name} from a server needs Shuttle Music Pro"
)
