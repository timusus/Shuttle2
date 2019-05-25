package au.com.simplecityapps.shuttle.imageloading.glide

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import au.com.simplecityapps.R
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.CompletionHandler
import au.com.simplecityapps.shuttle.imageloading.glide.module.GlideApp
import au.com.simplecityapps.shuttle.imageloading.glide.module.GlideRequest
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song

class GlideImageLoader : ArtworkImageLoader {

    sealed class LoadResult {
        object Success : LoadResult()
        object Failure : LoadResult()
    }

    override fun loadArtwork(imageView: ImageView, albumArtist: AlbumArtist, vararg options: ArtworkImageLoader.Options, completionHandler: CompletionHandler) {
        loadArtwork(imageView, albumArtist as Any, *options, completionHandler = completionHandler)
    }

    override fun loadArtwork(imageView: ImageView, album: Album, vararg options: ArtworkImageLoader.Options, completionHandler: CompletionHandler) {
        loadArtwork(imageView, album as Any, *options, completionHandler = completionHandler)
    }

    override fun loadArtwork(imageView: ImageView, song: Song, vararg options: ArtworkImageLoader.Options, completionHandler: CompletionHandler) {
        loadArtwork(imageView, song as Any, *options, completionHandler = completionHandler)
    }

    @DrawableRes
    var placeHolderResId: Int = R.drawable.ic_placeholder_light

    fun getRequestBuilder(context: Context, vararg options: ArtworkImageLoader.Options): GlideRequest<Drawable> {
        val glideRequest = GlideApp
            .with(context)
            .asDrawable()
            .placeholder(placeHolderResId)

        options.forEach { option ->
            when (option) {
                is ArtworkImageLoader.Options.CircleCrop -> {
                    glideRequest.apply(RequestOptions.circleCropTransform())
                }
                is ArtworkImageLoader.Options.RoundedCorners -> {
                    glideRequest.apply(RequestOptions.bitmapTransform(MultiTransformation(mutableListOf(CenterCrop(), RoundedCorners(option.radius)))))
                }
                is ArtworkImageLoader.Options.Priority -> {
                    when (option.priority) {
                        ArtworkImageLoader.Options.Priority.Priority.Low -> glideRequest.priority(Priority.LOW)
                        ArtworkImageLoader.Options.Priority.Priority.Default -> glideRequest.priority(Priority.NORMAL)
                        ArtworkImageLoader.Options.Priority.Priority.High -> glideRequest.priority(Priority.HIGH)
                        ArtworkImageLoader.Options.Priority.Priority.Max -> glideRequest.priority(Priority.IMMEDIATE)
                    }
                }
                is ArtworkImageLoader.Options.Crossfade -> {
                    glideRequest.transition(DrawableTransitionOptions.withCrossFade(option.duration))
                }
            }
        }

        return glideRequest
    }

    private fun <T> loadArtwork(imageView: ImageView, `object`: T, vararg options: ArtworkImageLoader.Options, completionHandler: CompletionHandler) {
        val glideRequest = getRequestBuilder(imageView.context, *options)

        completionHandler?.let {
            glideRequest.addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    completionHandler(LoadResult.Failure)
                    return false
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    completionHandler(LoadResult.Success)
                    return false
                }
            })
        }

        glideRequest
            .load(`object`)
            .into(imageView)
    }

    override fun clear(imageView: ImageView) {
        GlideApp
            .with(imageView.context)
            .clear(imageView)
    }
}