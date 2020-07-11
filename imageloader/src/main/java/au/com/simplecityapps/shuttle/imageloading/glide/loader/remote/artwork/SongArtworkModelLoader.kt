package au.com.simplecityapps.shuttle.imageloading.glide.loader.remote.artwork

import au.com.simplecityapps.shuttle.imageloading.glide.loader.common.encode
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import java.io.InputStream

class SongArtworkModelLoader(
    urlLoader: ModelLoader<GlideUrl, InputStream>,
    private val preferenceManager: GeneralPreferenceManager
) : BaseGlideUrlLoader<Song>(urlLoader) {

    override fun getUrl(model: Song, width: Int, height: Int, options: Options?): String {
        return "https://artwork.shuttlemusicplayer.app/api/v1/artwork?artist=${model.albumArtist.encode()}&album=${model.album.encode()}"
    }

    override fun handles(model: Song): Boolean {
        return true
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
            return SongArtworkModelLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java), preferenceManager)
        }

        override fun teardown() {}
    }
}