package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2

import au.com.simplecityapps.shuttle.imageloading.urlEncode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.settings.ArtworkSettings
import java.io.InputStream

class S2AlbumArtistArtworkModelLoader(
    private val urlLoader: ModelLoader<GlideUrl, InputStream>,
    private val artworkSettings: ArtworkSettings
) : ModelLoader<AlbumArtist, InputStream> {
    private fun getUrl(model: AlbumArtist): String = "https://api.shuttlemusicplayer.app/v1/artwork?artist=${(model.name ?: model.friendlyArtistName)!!.urlEncode()}"

    override fun handles(model: AlbumArtist): Boolean = (model.name ?: model.friendlyArtistName) != null

    override fun buildLoadData(
        model: AlbumArtist,
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
    ) : ModelLoaderFactory<AlbumArtist, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> = S2AlbumArtistArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java), artworkSettings)

        override fun teardown() {}
    }
}
