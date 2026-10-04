package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.test.utils.FakeDataSet
import androidx.media3.test.utils.FakeDataSource
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadCacheDataSourceTest {
    private val downloaded = Uri.parse("jellyfin://server/Audio/downloaded")
    private val streamed = Uri.parse("jellyfin://server/Audio/streamed")
    private val downloadedBytes = ByteArray(2048) { 1 }
    private val streamedBytes = ByteArray(512) { 2 }
    private val partial = Uri.parse("jellyfin://server/Audio/partial")

    private lateinit var cache: Cache

    // CacheDataSource creates its upstream up front, so look at what was opened, which only a cache miss does.
    private val upstreams = mutableListOf<FakeDataSource>()

    private val upstream = DataSource.Factory {
        FakeDataSource(
            FakeDataSet().newData(streamed).appendReadData(streamedBytes).endData()
                .newData(partial).appendReadData(streamedBytes).endData()
        ).also { upstreams += it }
    }

    private fun upstreamOpens() = upstreams.sumOf { it.getAndClearOpenedDataSpecs().size }

    @Before
    fun setUp() {
        cache = emptyDownloadCache(ApplicationProvider.getApplicationContext())
        // What the DownloadManager does: fetch the song into the cache, keyed by its path.
        val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory {
            FakeDataSource(FakeDataSet().newData(downloaded).appendReadData(downloadedBytes).endData())
        }.createDataSource()
        CacheWriter(source, DataSpec.Builder().setUri(downloaded).setKey(downloaded.toString()).build(), null, null).cache()
    }

    @After
    fun tearDown() {
        cache.release()
    }

    private fun read(uri: Uri): ByteArray {
        val source = downloadCacheDataSourceFactory(cache, upstream).createDataSource()
        source.open(DataSpec(uri))
        return try {
            source.readAllBytes()
        } finally {
            source.close()
        }
    }

    private fun DataSource.readAllBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val n = read(buffer, 0, buffer.size)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun `a downloaded song plays from the cache without opening upstream`() {
        read(downloaded).contentEquals(downloadedBytes) shouldBe true
        upstreamOpens() shouldBe 0
    }

    @Test
    fun `a song that isn't downloaded streams from upstream and isn't written to the cache`() {
        read(streamed).contentEquals(streamedBytes) shouldBe true
        upstreamOpens() shouldBe 1
        cache.keys.contains(streamed.toString()) shouldBe false
    }

    @Test
    fun `a partial download streams from upstream alone instead of stitching its spans to the stream`() {
        // A download that failed halfway: the first half is cached, the rest never arrived.
        val failing = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory {
            FakeDataSource(
                FakeDataSet().newData(partial)
                    .appendReadData(ByteArray(1024) { 1 })
                    .appendReadError(java.io.IOException("failed"))
                    .appendReadData(ByteArray(1024) { 1 })
                    .endData()
            )
        }.createDataSource()
        runCatching {
            CacheWriter(failing, DataSpec.Builder().setUri(partial).setKey(partial.toString()).build(), null, null).cache()
        }
        (cache.getCachedBytes(partial.toString(), 0, Long.MAX_VALUE) > 0) shouldBe true

        read(partial).contentEquals(streamedBytes) shouldBe true
    }
}
