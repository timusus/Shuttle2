package com.bumptech.glide

import android.content.Context
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.executor.directGlideExecutor
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Test hook: swaps the app's Glide for one that serves the sample library's covers
 * ([SampleLibrary]) for [Song]s, [Album]s and [AlbumArtist]s named after sample albums and
 * artists, so Robolectric and Roborazzi tests show real artwork. Anything else fails to load and
 * keeps its placeholder, as it does with no Glide hook at all.
 *
 * The replacement skips the app's `ImageLoaderGlideModule` (it needs Hilt, which unit tests don't
 * run), loads on the calling thread and never touches the disk cache. Production loading is
 * untouched. [install] before setting content; [uninstall] in an `@After`.
 *
 * Lives in Glide's package because building a Glide without its generated module is
 * package-private.
 */
object SampleArtworkGlide {
    fun install(context: Context) {
        val executor = directGlideExecutor()
        val glide = GlideBuilder()
            .setSourceExecutor(executor)
            .setDiskCacheExecutor(executor)
            .setAnimationExecutor(executor)
            .setDiskCache { null }
            .setDefaultRequestOptions(RequestOptions().disallowHardwareConfig().diskCacheStrategy(DiskCacheStrategy.NONE))
            .build(context.applicationContext, emptyList(), null)
        glide.registry
            .prepend(Song::class.java, InputStream::class.java, SampleCoverLoader.Factory { song: Song -> albumCover(song.album) })
            .prepend(Album::class.java, InputStream::class.java, SampleCoverLoader.Factory { album: Album -> albumCover(album.name) })
            .prepend(AlbumArtist::class.java, InputStream::class.java, SampleCoverLoader.Factory { artist: AlbumArtist -> artistCover(artist.name) })
        Glide.init(glide)
    }

    fun uninstall() = Glide.tearDown()

    private fun albumCover(title: String?): String? = title?.let(SampleLibrary::albumNamed)?.id

    private fun artistCover(name: String?): String? = SampleLibrary.artists.firstOrNull { it.name == name }?.coverAlbumId
}

private class SampleCoverLoader<T : Any>(
    private val coverFor: (T) -> String?,
) : ModelLoader<T, InputStream> {
    override fun handles(model: T): Boolean = coverFor(model) != null

    override fun buildLoadData(model: T, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        val albumId = coverFor(model) ?: return null
        return ModelLoader.LoadData(ObjectKey("sample-cover:$albumId"), CoverFetcher(albumId))
    }

    class Factory<T : Any>(private val coverFor: (T) -> String?) : ModelLoaderFactory<T, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<T, InputStream> = SampleCoverLoader(coverFor)

        override fun teardown() = Unit
    }
}

private class CoverFetcher(private val albumId: String) : DataFetcher<InputStream> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        callback.onDataReady(ByteArrayInputStream(SampleLibrary.coverBytes(albumId)))
    }

    override fun cleanup() = Unit

    override fun cancel() = Unit

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}
