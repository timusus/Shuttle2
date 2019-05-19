package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.encode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.simplecityapps.mediaprovider.model.AlbumArtist
import java.io.InputStream


class AlbumArtistArtworkModelLoader(urlLoader: ModelLoader<GlideUrl, InputStream>) : BaseGlideUrlLoader<AlbumArtist>(urlLoader) {

    override fun getUrl(model: AlbumArtist, width: Int, height: Int, options: Options?): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${model.name.encode()}"
    }

    override fun handles(model: AlbumArtist): Boolean {
        return true
    }

    class Factory : ModelLoaderFactory<AlbumArtist, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
            return AlbumArtistArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {}
    }
}

