package com.simplecityapps.imageloading.coil

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ArtworkFetcherTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()

    /** Serves every URL as its own bytes, recording the disk cache key each fetch was given. */
    private val requestedDiskCacheKeys = mutableListOf<String?>()
    private val fakeNetworkFetcherFactory =
        object : Fetcher.Factory<Uri> {
            override fun create(
                data: Uri,
                options: Options,
                imageLoader: ImageLoader
            ): Fetcher = object : Fetcher {
                override suspend fun fetch(): FetchResult {
                    requestedDiskCacheKeys += options.diskCacheKey
                    return SourceFetchResult(ImageSource(Buffer().writeUtf8(data.toString()), options.fileSystem), null, DataSource.NETWORK)
                }
            }
        }

    private val imageLoader =
        ImageLoader.Builder(context)
            .components { add(fakeNetworkFetcherFactory) }
            .diskCache {
                DiskCache.Builder()
                    .directory(tempFolder.newFolder("artwork").toOkioPath())
                    .build()
            }
            .build()

    @Test
    fun `falls through sources with nothing or that fail to the first with artwork`() = runBlocking<Unit> {
        val result =
            fetch(
                localSource { null },
                localSource { throw IOException("unreadable") },
                localSource { "cover".byteInputStream() },
                localSource { "not reached".byteInputStream() }
            )

        result.readUtf8() shouldBe "cover"
    }

    @Test
    fun `fails when no source has artwork`() {
        shouldThrow<ArtworkNotFoundException> {
            runBlocking { fetch(localSource { null }, remoteSource { null }) }
        }
    }

    @Test
    fun `local artwork is cached under the model's key, so the next load skips the sources`() = runBlocking<Unit> {
        fetch(localSource { "cover".byteInputStream() }).readUtf8()

        val cached = fetch(localSource { throw AssertionError("the cache should answer first") })

        cached.readUtf8() shouldBe "cover"
        (cached as SourceFetchResult).dataSource shouldBe DataSource.DISK
    }

    @Test
    fun `remote artwork is fetched through the network fetcher under the model's key`() = runBlocking<Unit> {
        val result = fetch(localSource { null }, remoteSource { "https://example.com/cover.jpg" })

        result.readUtf8() shouldBe "https://example.com/cover.jpg"
        requestedDiskCacheKeys shouldBe listOf(MODEL_KEY)
    }

    private suspend fun fetch(vararg sources: ArtworkSource<String>): FetchResult = ArtworkFetcher.Factory<String>(cacheKey = { MODEL_KEY }, sources = sources.toList())
        .create("model", Options(context), imageLoader)
        .fetch()!!

    private fun FetchResult.readUtf8(): String = (this as SourceFetchResult).source.use { it.source().readUtf8() }

    private fun localSource(open: () -> InputStream?) = object : ArtworkSource.Local<String> {
        override suspend fun open(model: String): InputStream? = open()
    }

    private fun remoteSource(url: () -> String?) = object : ArtworkSource.Remote<String> {
        override suspend fun url(model: String): String? = url()
    }

    private companion object {
        const val MODEL_KEY = "song:Artist_Album_Song"
    }
}
