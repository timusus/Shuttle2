package com.simplecityapps.shuttle.ui

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.allowHardware
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import okio.Buffer

/**
 * Test hook: swaps the app's Coil [ImageLoader] for one that serves the sample library's covers
 * ([SampleLibrary]) for [Song]s, [Album]s and [AlbumArtist]s named after sample albums and
 * artists, so Robolectric and Roborazzi tests show real artwork. Anything else fails to load and
 * keeps its placeholder, as it does with no hook at all.
 *
 * The replacement skips the app's `CoilModule` loader (it needs Hilt, which unit tests don't run),
 * loads on the calling thread and has no caches. Production loading is untouched. [install]
 * before setting content; [uninstall] in an `@After`.
 */
@OptIn(DelicateCoilApi::class)
object SampleArtworkCoil {
    fun install(context: Context) {
        val imageLoader = ImageLoader.Builder(context.applicationContext)
            .coroutineContext(Dispatchers.Unconfined)
            .memoryCache(null)
            .diskCache(null)
            .allowHardware(false)
            .components {
                add(SampleCoverFetcher.Factory { song: Song -> albumCover(song.album) })
                add(SampleCoverFetcher.Factory { album: Album -> albumCover(album.name) })
                add(SampleCoverFetcher.Factory { artist: AlbumArtist -> artistCover(artist.name) })
            }
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    fun uninstall() = SingletonImageLoader.reset()

    private fun albumCover(title: String?): String? = title?.let(SampleLibrary::albumNamed)?.id

    private fun artistCover(name: String?): String? = SampleLibrary.artists.firstOrNull { it.name == name }?.coverAlbumId
}

private class SampleCoverFetcher(
    private val albumId: String?,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val albumId = albumId ?: throw FileNotFoundException("No sample cover")
        val source = ImageSource(Buffer().write(SampleLibrary.coverBytes(albumId)), options.fileSystem)
        return SourceFetchResult(source, mimeType = null, dataSource = DataSource.MEMORY)
    }

    class Factory<T : Any>(private val coverFor: (T) -> String?) : Fetcher.Factory<T> {
        override fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher = SampleCoverFetcher(coverFor(data), options)
    }
}
