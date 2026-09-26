package com.simplecityapps.imageloading

import android.graphics.Bitmap

/** Artwork as bitmaps and bytes, for what draws outside Compose: the media session, Cast and the widgets. */
interface ArtworkImageLoader {
    /**
     * Loads [data] as a software bitmap no larger than [width] x [height], or null when it has no artwork. The returned request
     * cancels the load, after which [onCompletion] isn't called.
     */
    fun loadBitmap(
        data: Any,
        width: Int,
        height: Int,
        options: List<Options> = emptyList(),
        onCompletion: (Bitmap?) -> Unit
    ): Request

    /** [data]'s artwork at full size, encoded as a JPEG, or null when it has none. */
    suspend fun loadBitmap(data: Any): ByteArray?

    /** Empties the memory and disk caches, so artwork loads from its sources again. */
    suspend fun clearCache()

    fun interface Request {
        fun cancel()
    }

    sealed interface Options {
        /** Fills the requested size exactly, cropping whatever overflows it. */
        data object CenterCrop : Options
    }
}
