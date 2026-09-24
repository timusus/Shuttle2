package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Song
import java.io.File

data class MediaInfo(val path: Uri, val mimeType: String, val isRemote: Boolean)

interface MediaInfoProvider {
    @Throws(IllegalStateException::class)
    fun handles(uri: Uri): Boolean

    suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean = false
    ): MediaInfo

    /**
     * The URL of [song]'s original, non-transcoded file, for offline download. Null when this
     * provider can't produce one (e.g. auth failure).
     */
    suspend fun downloadUri(song: Song): Uri?
}

class AggregateMediaInfoProvider(val providers: MutableSet<MediaInfoProvider> = mutableSetOf()) : MediaInfoProvider {
    fun addProvider(provider: MediaInfoProvider) {
        providers.add(provider)
    }

    fun removeProvider(provider: MediaInfoProvider) {
        providers.remove(provider)
    }

    override fun handles(uri: Uri): Boolean = true

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val uri = uriFor(song)
        return providers.firstOrNull { it.handles(uri) }?.getMediaInfo(song, castCompatibilityMode)
            ?: MediaInfo(path = uri, mimeType = song.mimeType, isRemote = false)
    }

    // Local songs are already on disk, so there's nothing to download; only a remote provider
    // (matched below by scheme) can produce a download URL.
    override suspend fun downloadUri(song: Song): Uri? = providers.firstOrNull { it.handles(uriFor(song)) }?.downloadUri(song)

    // MediaStore songs carry raw file paths, which may contain '#' or '?', so they're built as
    // file URIs rather than parsed. Everything else (content://, emby://, jellyfin://, plex://)
    // is already a URI, and parsing keeps its scheme so the matching provider handles it.
    private fun uriFor(song: Song): Uri = if (song.path.startsWith("/")) {
        Uri.fromFile(File(song.path))
    } else {
        Uri.parse(song.path)
    }
}
