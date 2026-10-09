package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.io.File

/**
 * Where to play a song from. [timeSeek] is set for a stream that seeks by re-requesting it from a time, not by byte range.
 * [isReplaceableTranscode] for a transcode the server drops when another starts (Plex runs one per user), so its stream
 * can answer 404 mid-play; opening it again starts it again.
 */
data class MediaInfo(
    val path: Uri,
    val mimeType: String,
    val isRemote: Boolean,
    val timeSeek: TimeSeekableStream? = null,
    val isReplaceableTranscode: Boolean = false
)

/** Where to download [song] from, and the MIME type of what's actually at [uri] — a provider that transcodes an
 * undecodable format for download (see [MediaInfoProvider.downloadInfo]) returns the transcode's MIME type here,
 * not the song's original one, so the download is recorded as what it actually is. */
data class DownloadInfo(val uri: Uri, val mimeType: String)

interface MediaInfoProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    @Throws(IllegalStateException::class)
    fun handles(scheme: String?): Boolean

    /** [playId] is the play the stream is opened for, which a server's playback reports for it then name. */
    suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean = false,
        playId: String? = null
    ): MediaInfo

    /**
     * Where to download [song]'s file from, and its real MIME type — [song]'s original, non-transcoded file where
     * the player can decode it, otherwise a playable transcode. Null when this provider can't produce one (e.g. auth
     * failure).
     */
    suspend fun downloadInfo(song: Song): DownloadInfo?

    /**
     * A fallback download for [song], used when [downloadInfo]'s URL was rejected by the server
     * with the given [responseCode] (401 or 403). A 403 persists the change, so later downloads
     * for this provider go straight to the fallback; a 401 does not, since it can also mean the
     * cached session expired rather than a permission change. A song the player can't decode as it
     * is falls back to the same transcode [downloadInfo] gives. Null when this provider has no
     * distinct fallback to offer.
     */
    suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo?

    /**
     * Whether [song]'s original file can't be decoded on this platform, so [downloadInfo] gives a transcode of
     * it: a download recorded as the original type is then silent, and is downloaded again.
     */
    fun downloadsAsTranscode(song: Song): Boolean = false
}

/** Whether a song from a remote server may be streamed. Asked once per song, when its stream is resolved. */
fun interface ServerStreamPolicy {
    suspend fun allows(song: Song): Boolean

    companion object {
        val AllowAll = ServerStreamPolicy { true }
    }
}

/** [ServerStreamPolicy] refused a song's stream. */
class ServerStreamDeniedException(song: Song) : IllegalStateException("Streaming ${song.name} from its server isn't allowed")

/**
 * Resolves a song through the provider that handles it, or as a local file if none does.
 *
 * @param streamPolicy asked before a remote song's stream is resolved, so every player (local, Cast, Android Auto)
 * goes through the same check. A refusal throws [ServerStreamDeniedException].
 */
class AggregateMediaInfoProvider(
    private val providers: Collection<MediaInfoProvider> = emptyList(),
    private val streamPolicy: ServerStreamPolicy = ServerStreamPolicy.AllowAll
) : MediaInfoProvider {
    override fun handles(scheme: String?): Boolean = true

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo {
        val provider = providers.firstOrNull { it.handles(schemeOf(song.path)) }
            ?: return MediaInfo(path = uriFor(song.path), mimeType = song.mimeType, isRemote = false)
        if (!streamPolicy.allows(song)) throw ServerStreamDeniedException(song)
        return provider.getMediaInfo(song, castCompatibilityMode, playId)
    }

    // Local songs are already on disk, so there's nothing to download; only a remote provider
    // (matched below by scheme) can produce a download URL.
    override suspend fun downloadInfo(song: Song): DownloadInfo? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.downloadInfo(song)

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.downloadFallbackInfo(song, responseCode)

    override fun downloadsAsTranscode(song: Song): Boolean = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.downloadsAsTranscode(song) ?: false

    // MediaStore songs carry raw file paths, which may contain '#' or '?', so they're built as
    // file URIs rather than parsed. Everything else (content://, emby://, jellyfin://, plex://)
    // is already a URI, and parsing keeps its scheme so the matching provider handles it.
    private fun uriFor(path: String): Uri = if (path.startsWith("/")) {
        Uri.fromFile(File(path))
    } else {
        Uri.parse(path)
    }
}
