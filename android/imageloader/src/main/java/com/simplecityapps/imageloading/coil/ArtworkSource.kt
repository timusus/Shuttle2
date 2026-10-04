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

/**
 * This source, tried for the [part] of a model it stands for: an artist hero's artist or fallback album. It has nothing
 * for a model whose part is null.
 */
internal fun <T : Any, R : Any> ArtworkSource<T>.on(part: (R) -> T?): ArtworkSource<R> = when (this) {
    is ArtworkSource.Local -> LocalPart(this, part)
    is ArtworkSource.Remote -> RemotePart(this, part)
}

private class LocalPart<T : Any, R : Any>(
    private val source: ArtworkSource.Local<T>,
    private val part: (R) -> T?,
) : ArtworkSource.Local<R> {
    override fun handles(model: R): Boolean = part(model)?.let(source::handles) ?: false

    override suspend fun open(model: R): InputStream? = part(model)?.let { source.open(it) }

    override fun toString(): String = "${source::class.simpleName}"
}

private class RemotePart<T : Any, R : Any>(
    private val source: ArtworkSource.Remote<T>,
    private val part: (R) -> T?,
) : ArtworkSource.Remote<R> {
    override fun handles(model: R): Boolean = part(model)?.let(source::handles) ?: false

    override suspend fun url(model: R): String? = part(model)?.let { source.url(it) }

    override fun toString(): String = "${source::class.simpleName}"
}
