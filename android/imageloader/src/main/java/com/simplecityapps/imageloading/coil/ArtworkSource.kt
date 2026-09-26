package com.simplecityapps.imageloading.coil

import java.io.InputStream

/** One place a model's artwork can come from. [ArtworkFetcher] tries a model's sources in order until one yields an image. */
sealed interface ArtworkSource<T : Any> {
    /** Whether to try this source for [model]. Checked per request, so a settings change applies to the next load. */
    fun handles(model: T): Boolean = true

    /** Artwork read from the device. A null stream means this source has nothing for the model. */
    interface Local<T : Any> : ArtworkSource<T> {
        suspend fun open(model: T): InputStream?
    }

    /** Artwork downloaded through the image loader's network fetcher. A null URL means this source has nothing for the model. */
    interface Remote<T : Any> : ArtworkSource<T> {
        suspend fun url(model: T): String?
    }
}
