package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.simplecityapps.mediaprovider.model.Album
import java.io.InputStream


class AlbumArtworkModelLoader(urlLoader: ModelLoader<GlideUrl, InputStream>) : BaseGlideUrlLoader<Album>(urlLoader) {

    override fun getUrl(model: Album, width: Int, height: Int, options: Options?): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${model.albumArtistName}&album=${model.name}"
    }

    override fun handles(model: Album): Boolean {
        return true
    }

    class Factory : ModelLoaderFactory<Album, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Album, InputStream> {
            return AlbumArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {}
    }
}

