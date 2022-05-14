package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.s2

import au.com.simplecityapps.shuttle.imageloading.urlEncode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.InputStream

class S2SongArtworkModelLoader(
    urlLoader: ModelLoader<GlideUrl, InputStream>,
    private val preferenceManager: GeneralPreferenceManager
) : BaseGlideUrlLoader<Song>(urlLoader) {

    override fun getUrl(model: Song, width: Int, height: Int, options: Options?): String {
        return "https://api.shuttlemusicplayer.app/v1/artwork?artist=${(model.albumArtist ?: model.friendlyArtistName)!!.urlEncode()}&album=${model.album!!.urlEncode()}"
    }

    override fun handles(model: Song): Boolean {
        return model.album != null && (model.albumArtist ?: model.friendlyArtistName) != null
    }

    override fun buildLoadData(model: Song, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? {
        if (preferenceManager.artworkLocalOnly) {
            return null
        }
        return super.buildLoadData(model, width, height, options)
    }

    class Factory(
        private val preferenceManager: GeneralPreferenceManager
    ) : ModelLoaderFactory<Song, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return S2SongArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java), preferenceManager)
        }

        override fun teardown() {}
    }
}
