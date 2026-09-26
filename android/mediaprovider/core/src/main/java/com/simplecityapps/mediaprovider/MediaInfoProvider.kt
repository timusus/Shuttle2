package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dagger.MapKey
import java.io.File

data class MediaInfo(val path: Uri, val mimeType: String, val isRemote: Boolean)

interface MediaInfoProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    @Throws(IllegalStateException::class)
    fun handles(scheme: String?): Boolean

    suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean = false
    ): MediaInfo

    /**
     * The URL of [song]'s original, non-transcoded file, for offline download. Null when this
     * provider can't produce one (e.g. auth failure).
     */
    suspend fun downloadUri(song: Song): Uri?

    /**
     * A fallback download URL for [path] (a `Song.path`), used when [downloadUri]'s URL was
     * rejected by the server with the given [responseCode] (401 or 403). A 403 persists the
     * change, so later downloads for this provider go straight to the fallback; a 401 does not,
     * since it can also mean the cached session expired rather than a permission change. Null when
     * this provider isn't the one that produced [path], or has no distinct fallback to offer.
     */
    suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri?
}

/**
 * Keys a remote provider's [MediaInfoProvider] binding by the provider it resolves songs for. Each provider module
 * contributes its own entry (`@IntoMap`), so a module that needs every provider's resolver (playback) asks for the map
 * rather than depending on the provider modules.
 */
@MapKey
annotation class MediaProviderTypeKey(val value: MediaProviderType)

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
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val provider = providers.firstOrNull { it.handles(schemeOf(song.path)) }
            ?: return MediaInfo(path = uriFor(song.path), mimeType = song.mimeType, isRemote = false)
        if (!streamPolicy.allows(song)) throw ServerStreamDeniedException(song)
        return provider.getMediaInfo(song, castCompatibilityMode)
    }

    // Local songs are already on disk, so there's nothing to download; only a remote provider
    // (matched below by scheme) can produce a download URL.
    override suspend fun downloadUri(song: Song): Uri? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.downloadUri(song)

    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = providers.firstOrNull { it.handles(schemeOf(path)) }?.downloadFallbackUri(path, responseCode)

    // MediaStore songs carry raw file paths, which may contain '#' or '?', so they're built as
    // file URIs rather than parsed. Everything else (content://, emby://, jellyfin://, plex://)
    // is already a URI, and parsing keeps its scheme so the matching provider handles it.
    private fun uriFor(path: String): Uri = if (path.startsWith("/")) {
        Uri.fromFile(File(path))
    } else {
        Uri.parse(path)
    }
}

/**
 * The scheme a [MediaInfoProvider] matches [path] against, without building a [Uri] — the only place that still
 * needs one is [AggregateMediaInfoProvider]'s local-file fallback, so provider matching (including from plain-JVM
 * tests) stays off `Uri.parse`/`Uri.fromFile`, which aren't mocked outside Robolectric (#552).
 */
private fun schemeOf(path: String): String? = if (path.startsWith("/")) {
    "file"
} else {
    path.substringBefore("://", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
}
