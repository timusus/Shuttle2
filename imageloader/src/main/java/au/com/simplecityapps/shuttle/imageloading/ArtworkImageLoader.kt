package au.com.simplecityapps.shuttle.imageloading

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.annotation.WorkerThread
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecity.amp_library.glide.palette.ColorSet
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

        class Crossfade(val duration: Int) : Options()
    }

    fun loadArtwork(imageView: ImageView, albumArtist: AlbumArtist, vararg options: Options, completionHandler: CompletionHandler = null)

    fun loadArtwork(imageView: ImageView, album: Album, vararg options: Options, completionHandler: CompletionHandler = null)

    fun loadArtwork(imageView: ImageView, song: Song, vararg options: Options, completionHandler: CompletionHandler = null)

    fun loadBitmap(song: Song, width: Int, height: Int, vararg options: Options, completionHandler: (Bitmap?) -> Unit)

    @WorkerThread
    fun loadBitmap(song: Song): ByteArray?

    fun loadColorSet(song: Song, callback: (ColorSet?) -> Unit)

    fun clear(imageView: ImageView)
}