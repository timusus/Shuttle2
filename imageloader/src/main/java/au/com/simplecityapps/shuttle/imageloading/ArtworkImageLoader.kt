package au.com.simplecityapps.shuttle.imageloading

import android.widget.ImageView
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song

interface ArtworkImageLoader {

    sealed class Options {
        object CircleCrop : Options()

        /**
         * @param radius corner radius, in DP
         */
        class RoundedCorners(val radius: Int) : Options()
    }

    fun loadArtwork(imageView: ImageView, albumArtist: AlbumArtist, vararg  options: Options)

    fun loadArtwork(imageView: ImageView, album: Album, vararg  options: Options)

    fun loadArtwork(imageView: ImageView, song: Song, vararg  options: Options)

    fun clear(imageView: ImageView)
}