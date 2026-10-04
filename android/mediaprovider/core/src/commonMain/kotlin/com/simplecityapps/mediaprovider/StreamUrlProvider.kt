package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * The URL a remote song plays from, signed in to its server and capped by the current [StreamingBitrateCap], and the
 * one it downloads from for offline play: the platform-neutral half of a provider's [MediaInfoProvider], which the iOS
 * player and downloads resolve songs through.
 */
interface StreamUrlProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    fun handles(scheme: String?): Boolean

    /**
     * [startPositionMs] starts a transcode that far into the song, for a seek in a progressive transcode, which can't be
     * range-seeked; a direct-play stream starts at the beginning either way.
     *
     * @throws IllegalStateException when the server can't be signed in to, or the URL can't be built.
     */
    fun streamUrl(
        song: Song,
        startPositionMs: Long = 0
    ): String

    /**
     * Where to download [song]'s file for offline play: its original, untranscoded file where this platform's player
     * decodes it, otherwise a playable transcode. Null when the server isn't signed in to or the URL can't be built,
     * and always for a local provider, whose songs are already on the device.
     */
    fun downloadSource(song: Song): DownloadSource? = null
}

/**
 * A download's URL, and the MIME type of what's actually there: a transcoded download's, not the song's original one,
 * so the file is recorded as what it is.
 */
data class DownloadSource(
    val url: String,
    val mimeType: String
)

/** The provider among these that handles [path] (a `Song.path`), or null for a local song. */
fun Collection<StreamUrlProvider>.forPath(path: String): StreamUrlProvider? = firstOrNull { it.handles(schemeOf(path)) }
