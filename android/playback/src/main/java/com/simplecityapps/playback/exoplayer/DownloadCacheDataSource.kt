package com.simplecityapps.playback.exoplayer

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource

/**
 * Reads from [downloadCache] what's been downloaded, and from [upstream] the rest. A download is keyed by its song's
 * path, which is also the URI the player opens it by, so Media3's default cache key finds it with no lookup, and a
 * downloaded song plays without touching [upstream] (so without resolving its stream or reaching its server).
 *
 * Read-only: streaming a song never writes into the cache, which holds downloads and nothing else.
 */
@UnstableApi
fun downloadCacheDataSourceFactory(
    downloadCache: Cache,
    upstream: DataSource.Factory
): DataSource.Factory = CacheDataSource.Factory()
    .setCache(downloadCache)
    .setCacheWriteDataSinkFactory(null)
    .setUpstreamDataSourceFactory(upstream)
    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
