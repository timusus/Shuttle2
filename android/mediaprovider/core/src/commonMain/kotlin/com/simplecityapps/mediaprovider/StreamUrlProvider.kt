package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * The URL a remote song plays from, signed in to its server and capped by the current [StreamingBitrateCap]: the
 * platform-neutral half of a provider's [MediaInfoProvider], which the iOS player resolves songs through.
 */
interface StreamUrlProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    fun handles(scheme: String?): Boolean

    /** @throws IllegalStateException when the server can't be signed in to, or the URL can't be built. */
    fun streamUrl(song: Song): String
}

/** The provider among these that handles [path] (a `Song.path`), or null for a local song. */
fun Collection<StreamUrlProvider>.forPath(path: String): StreamUrlProvider? = firstOrNull { it.handles(schemeOf(path)) }
