package com.simplecityapps.imageloading.coil

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toUri
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.BufferedSource
import okio.source
import timber.log.Timber

/**
 * Loads a model's artwork from the first of its [sources] that has any, falling through the rest when one has nothing, fails,
 * or has only an image smaller than its [ArtworkSource.minimumSize].
 *
 * Coil picks a single fetcher per model, so this is where the source chain lives. Whatever a source returns is stored in the
 * disk cache under the model's key: local artwork is written there once read, and remote artwork is cached by the network
 * fetcher under the same key, so a later load skips the chain entirely.
 */
internal class ArtworkFetcher<T : Any>(
    private val model: T,
    private val diskCacheKey: String,
    private val sources: List<ArtworkSource<T>>,
    private val options: Options,
    private val imageLoader: ImageLoader
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        readDiskCache()?.let { return it }
        for (source in sources) {
            val result =
                try {
                    when (source) {
                        is ArtworkSource.Local -> fetchLocal(source)
                        is ArtworkSource.Remote -> fetchRemote(source)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.v("${source::class.simpleName} failed for $diskCacheKey (${e.message})")
                    null
                }
            if (result != null) return result
        }
        throw ArtworkNotFoundException(diskCacheKey)
    }

    private suspend fun fetchLocal(source: ArtworkSource.Local<T>): FetchResult? {
        val buffer = Buffer()
        val stream = source.open(model) ?: return null
        stream.source().use { input ->
            // Read in chunks, so a cancelled load stops reading and closes the stream
            while (input.read(buffer, READ_CHUNK_BYTES) != -1L) {
                currentCoroutineContext().ensureActive()
            }
        }
        if (buffer.size == 0L) return null
        if (!buffer.isAtLeast(source.minimumSize)) {
            Timber.v("${source::class.simpleName}'s image is under ${source.minimumSize}px for $diskCacheKey")
            return null
        }
        return SourceFetchResult(
            source = writeDiskCache(buffer) ?: ImageSource(buffer, options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    private suspend fun fetchRemote(source: ArtworkSource.Remote<T>): FetchResult? {
        val url = source.url(model) ?: return null
        val (fetcher, _) = imageLoader.components.newFetcher(url.toUri(), options.copy(diskCacheKey = diskCacheKey), imageLoader) ?: return null
        val result = fetcher.fetch()
        if (source.minimumSize > 0 && result is SourceFetchResult && !result.source.source().peek().isAtLeast(source.minimumSize)) {
            // The network fetcher has cached it under the model's key already; drop it, or the next load would read it back
            Timber.v("${source::class.simpleName}'s image is under ${source.minimumSize}px for $diskCacheKey")
            result.source.close()
            imageLoader.diskCache?.remove(diskCacheKey)
            return null
        }
        return result
    }

    /** Whether this image is at least [minimumSize] pixels on its shorter side; one whose size can't be read passes, for the decoder to judge. */
    private fun BufferedSource.isAtLeast(minimumSize: Int): Boolean {
        if (minimumSize <= 0) return true
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        peek().inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return true
        return minOf(bounds.outWidth, bounds.outHeight) >= minimumSize
    }

    private fun readDiskCache(): FetchResult? {
        if (!options.diskCachePolicy.readEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val snapshot = diskCache.openSnapshot(diskCacheKey) ?: return null
        return SourceFetchResult(snapshot.toImageSource(diskCache), mimeType = null, dataSource = DataSource.DISK)
    }

    private fun writeDiskCache(buffer: Buffer): ImageSource? {
        if (!options.diskCachePolicy.writeEnabled) return null
        val diskCache = imageLoader.diskCache ?: return null
        val editor = diskCache.openEditor(diskCacheKey) ?: return null
        return try {
            diskCache.fileSystem.write(editor.data) { writeAll(buffer.peek()) }
            editor.commitAndOpenSnapshot()?.toImageSource(diskCache)
        } catch (e: IOException) {
            Timber.v("Failed to cache artwork for $diskCacheKey (${e.message})")
            runCatching { editor.abort() }
            null
        }
    }

    private fun DiskCache.Snapshot.toImageSource(diskCache: DiskCache): ImageSource = ImageSource(file = data, fileSystem = diskCache.fileSystem, diskCacheKey = diskCacheKey, closeable = this)

    class Factory<T : Any>(
        private val cacheKey: (T) -> String,
        private val sources: List<ArtworkSource<T>>
    ) : Fetcher.Factory<T> {
        override fun create(
            data: T,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = ArtworkFetcher(
            model = data,
            diskCacheKey = options.diskCacheKey ?: cacheKey(data),
            sources = sources.filter { source -> source.handles(data) },
            options = options,
            imageLoader = imageLoader
        )
    }

    private companion object {
        const val READ_CHUNK_BYTES = 64L * 1024
    }
}

class ArtworkNotFoundException(key: String) : IOException("No artwork for $key")
