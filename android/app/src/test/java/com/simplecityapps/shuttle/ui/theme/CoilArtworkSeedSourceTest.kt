package com.simplecityapps.shuttle.ui.theme

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CoilArtworkSeedSourceTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    /** Serves each song's artwork from [artwork], by title and then by album, failing the load when it has none. */
    private val artwork = mutableMapOf<String, ByteArray>()

    private val imageLoader =
        ImageLoader.Builder(context)
            .coroutineContext(Dispatchers.Unconfined)
            .memoryCache(null)
            .diskCache(null)
            .components {
                add(
                    Fetcher.Factory<Song> { song, options, _ ->
                        Fetcher {
                            val bytes = artwork[song.name] ?: artwork[song.album] ?: throw IOException("No artwork for ${song.album}")
                            SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), null, DataSource.MEMORY) as FetchResult
                        }
                    },
                )
            }
            .build()

    private val seedSource = CoilArtworkSeedSource(context, imageLoader)

    @Test
    fun `artwork of a solid colour seeds the scheme`() = runBlocking<Unit> {
        artwork["Blue"] = solidPng(0xFF1E88E5.toInt())

        seedSource.seedFor(createSong(album = "Blue")).shouldBeInstanceOf<ArtworkSeed.Available>()
    }

    @Test
    fun `artwork that fails to load gives no seed`() = runBlocking<Unit> {
        seedSource.seedFor(createSong(album = "Missing")) shouldBe ArtworkSeed.None
    }

    @Test
    fun `a failed load isn't remembered, so the album seeds once its artwork loads`() = runBlocking<Unit> {
        val song = createSong(album = "Late")
        seedSource.seedFor(song) shouldBe ArtworkSeed.None

        artwork["Late"] = solidPng(0xFF1E88E5.toInt())

        seedSource.seedFor(song).shouldBeInstanceOf<ArtworkSeed.Available>()
    }

    @Test
    fun `tracks on one album with their own artwork get their own seeds`() = runBlocking<Unit> {
        artwork["Red"] = solidPng(0xFFE53935.toInt())
        artwork["Blue"] = solidPng(0xFF1E88E5.toInt())

        val red = seedSource.seedFor(createSong(id = 1, name = "Red", album = "Mixed"))
        val blue = seedSource.seedFor(createSong(id = 2, name = "Blue", album = "Mixed"))

        red.shouldBeInstanceOf<ArtworkSeed.Available>()
        blue.shouldBeInstanceOf<ArtworkSeed.Available>()
        red shouldNotBe blue
    }

    private fun solidPng(argb: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
        return Buffer().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it.outputStream()) }.readByteArray()
    }
}
