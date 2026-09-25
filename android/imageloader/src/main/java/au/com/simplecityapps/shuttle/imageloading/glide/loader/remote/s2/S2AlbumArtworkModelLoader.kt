package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2

import au.com.simplecityapps.shuttle.imageloading.urlEncode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.settings.ArtworkSettings
import java.io.InputStream

class S2AlbumArtworkModelLoader(
    private val urlLoader: ModelLoader<GlideUrl, InputStream>,
    private val artworkSettings: ArtworkSettings
) : ModelLoader<Album, InputStream> {
    private fun getUrl(model: Album): String = "https://api.shuttlemusicplayer.app/v1/artwork?artist=${(model.albumArtist ?: model.friendlyArtistName)!!.urlEncode()}&album=${model.name!!.urlEncode()}"

    override fun handles(model: Album): Boolean = model.name != null && (model.albumArtist ?: model.friendlyArtistName) != null

    override fun buildLoadData(
        model: Album,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        if (artworkSettings.localOnly.value) {
            return null
        }
        return urlLoader.buildLoadData(VersionedGlideUrl(getUrl(model), model.artworkVersion), width, height, options)
    }

    class Factory(
        private val artworkSettings: ArtworkSettings
    ) : ModelLoaderFactory<Album, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> = S2AlbumArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java), artworkSettings)

        override fun teardown() {}
    }
}
