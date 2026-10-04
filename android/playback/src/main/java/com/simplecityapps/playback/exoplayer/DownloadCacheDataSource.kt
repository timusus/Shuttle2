package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata

/**
 * Reads a song from [downloadCache] when its whole download is there, and from [upstream] otherwise. A download is
 * keyed by its song's path, which is also the URI the player opens it by, so Media3's default cache key finds it with
 * no lookup, and a downloaded song plays without touching [upstream] (so without resolving its stream or reaching its
 * server).
 *
 * A partial download (queued, failed or stopped) is never read: its spans came from the download URL, which can serve
 * a different stream than [upstream]'s (a transcode), so filling its holes from [upstream] would stitch two streams.
 * It streams from [upstream] alone instead.
 *
 * Read-only: streaming a song never writes into the cache, which holds downloads and nothing else.
 */
@UnstableApi
fun downloadCacheDataSourceFactory(
    downloadCache: Cache,
    upstream: DataSource.Factory
): DataSource.Factory {
    val cached = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setCacheWriteDataSinkFactory(null)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    return DataSource.Factory { CompleteDownloadsOnlyDataSource(downloadCache, cached, upstream) }
}

@UnstableApi
private class CompleteDownloadsOnlyDataSource(
    private val cache: Cache,
    private val cachedFactory: DataSource.Factory,
    private val upstreamFactory: DataSource.Factory
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var opened: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        val source = (if (isFullyCached(dataSpec)) cachedFactory else upstreamFactory).createDataSource()
        listeners.forEach(source::addTransferListener)
        opened = source
        return source.open(dataSpec)
    }

    private fun isFullyCached(dataSpec: DataSpec): Boolean {
        val key = dataSpec.key ?: dataSpec.uri.toString()
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length != C.LENGTH_UNSET.toLong() && cache.isCached(key, 0, length)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Int = checkNotNull(opened).read(buffer, offset, length)

    override fun getUri(): Uri? = opened?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = opened?.responseHeaders ?: emptyMap()

    override fun close() {
        opened?.close()
        opened = null
    }
}
