package au.com.simplecityapps.shuttle.imageloading

import android.widget.ImageView
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song

typealias CompletionHandler = ((GlideImageLoader.LoadResult) -> Unit)?

interface ArtworkImageLoader {

    sealed class Options {
        object CircleCrop : Options()

        /**
         * @param radius corner radius, in DP
         */
        class RoundedCorners(val radius: Int) : Options()

        class Priority(val priority: Priority) : Options() {

            enum class Priority {
                Low, Default, High, Max
            }
        }
    }

    fun loadArtwork(imageView: ImageView, albumArtist: AlbumArtist, vararg options: Options, completionHandler: CompletionHandler)

    fun loadArtwork(imageView: ImageView, album: Album, vararg options: Options, completionHandler: CompletionHandler)

    fun loadArtwork(imageView: ImageView, song: Song, vararg options: Options, completionHandler: CompletionHandler)

    fun clear(imageView: ImageView)
}