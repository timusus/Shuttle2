package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork

import au.com.simplecityapps.shuttle.imageloading.coil.encode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.friendlyNameOrArtistName
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.InputStream

class AlbumArtistArtworkModelLoader(
    urlLoader: ModelLoader<GlideUrl, InputStream>,
    private val preferenceManager: GeneralPreferenceManager
) : BaseGlideUrlLoader<AlbumArtist>(urlLoader) {

    override fun getUrl(model: AlbumArtist, width: Int, height: Int, options: Options?): String {
        return "https://api.shuttlemusicplayer.app/v1/artwork?artist=${model.friendlyNameOrArtistName.encode()}"
    }

    override fun handles(model: AlbumArtist): Boolean {
        return true
    }

    override fun buildLoadData(model: AlbumArtist, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (preferenceManager.artworkLocalOnly) {
            return null
        }
        return super.buildLoadData(model, width, height, options)
    }


    class Factory(
        private val preferenceManager: GeneralPreferenceManager
    ) : ModelLoaderFactory<AlbumArtist, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AlbumArtist, InputStream> {
            return AlbumArtistArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java), preferenceManager)
        }

        override fun teardown() {}
    }
}