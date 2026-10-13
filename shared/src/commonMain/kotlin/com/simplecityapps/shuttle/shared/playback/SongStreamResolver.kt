package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.mediaprovider.StreamUrlProvider
import com.simplecityapps.mediaprovider.forPath
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.shared.percentEncodedPath

/**
 * What the engine opens for a song: a server song's authenticated stream URL from the provider that handles its path
 * (`jellyfin://item/...`), a file path as a `file://` URL, and anything else as it is. A server stream opens at a
 * position: its URL carries the start as `StartTimeTicks`, which a transcode starts from and direct play ignores.
 *
 * Each stream carries the song's ReplayGain tags; the player applies the mode and preamp to them as it plays
 * ([IosAudioPlayer.setReplayGain]), so a change reaches the playing song. A provider that can't build a URL (signed out, no
 * address) throws, which fails the song as the controller expects, and so does a server song that
 * [serverStreamAccess] refuses (streaming needs Shuttle Music Pro or the trial), so the controller skips it. One it
 * can't decide yet (StoreKit hasn't answered) throws [ServerStreamNotAllowedException] marked
 * [ServerStreamNotAllowedException.undecided], which the controller doesn't hold against the song.
 *
 * A song from this device's library (`s2local://...`) plays from the file [localFiles] finds for it, which seeks like any
 * file, so it opens at the start; a file that's out of reach throws, failing the song. It isn't a server stream, so
 * [serverStreamAccess] is never asked about it.
 *
 * A server song that's been downloaded ([downloadedFile], `OfflineDownloads.fileUrl`) plays from its file, so it plays
 * offline and opens at the start. [serverStreamAccess] isn't asked: a song already downloaded never disappears when the
 * trial lapses, as Android's `EntitledServerStreamPolicy` allows a completed download before it asks the gate. One whose file has gone or is empty has no [downloadedFile]
 * (`OfflineDownloads` forgets it), so it streams.
 */
class SongStreamResolver(
    private val streamUrls: Collection<StreamUrlProvider>,
    private val serverStreamAccess: suspend (song: Song, playRequested: Boolean) -> ServerAccess = { _, _ -> ServerAccess.Allowed },
    private val localFiles: StreamUrlProvider? = null,
    private val downloadedFile: (path: String) -> String? = { null }
) : IosStreamResolver {
    override suspend fun resolve(
        song: Song,
        startPositionMs: Long,
        playRequested: Boolean,
        playId: String
    ): IosStream {
        val provider = streamUrls.forPath(song.path)
        val localFile = listOfNotNull(localFiles).forPath(song.path)
        val downloaded = if (localFile == null && provider != null) downloadedFile(song.path) else null
        if (localFile == null && downloaded == null && provider != null) {
            when (serverStreamAccess(song, playRequested)) {
                ServerAccess.Allowed -> Unit
                ServerAccess.Refused -> throw ServerStreamNotAllowedException(song, undecided = false)
                ServerAccess.Undecided -> throw ServerStreamNotAllowedException(song, undecided = true)
            }
        }
        val stream = when {
            localFile != null -> IosStream(url = localFile.streamUrl(song, startPositionMs))

            downloaded != null -> IosStream(url = downloaded)

            provider != null -> provider.openStream(song, startPositionMs, playId).let {
                IosStream(
                    url = it.url,
                    opensAtPosition = true,
                    transcoded = it.transcoded,
                    bitrateKbps = song.bitRate?.takeIf { rate -> rate > 0 } ?: -1,
                    sizeBytes = song.size.takeIf { size -> size > 0 && startPositionMs == 0L } ?: -1,
                    durationMs = (song.duration - startPositionMs).takeIf { duration -> duration > 0 } ?: -1
                )
            }

            song.path.startsWith("/") -> IosStream(url = fileUrl(song.path))

            else -> IosStream(url = song.path)
        }
        return stream.copy(replayGainTrackDb = song.replayGainTrack?.toFloat(), replayGainAlbumDb = song.replayGainAlbum?.toFloat())
    }

    override suspend fun endPlay(
        song: Song,
        playId: String
    ) {
        streamUrls.forPath(song.path)?.endPlay(playId)
    }

    /** The engine parses the URL, so the path's spaces and reserved characters are escaped, each segment on its own. */
    private fun fileUrl(path: String): String = "file://" + path.percentEncodedPath()
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
