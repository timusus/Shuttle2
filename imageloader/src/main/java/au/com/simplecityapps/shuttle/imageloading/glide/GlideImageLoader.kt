package au.com.simplecityapps.shuttle.imageloading.glide

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import au.com.simplecityapps.R
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.module.GlideApp
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSet
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

class GlideImageLoader : ArtworkImageLoader {

    var requestManager: RequestManager

    constructor(fragment: Fragment) {
        this.requestManager = GlideApp.with(fragment)
    }

    constructor(activity: Activity) {
        this.requestManager = GlideApp.with(activity)
    }

    constructor(context: Context) {
        this.requestManager = GlideApp.with(context)
    }

    override fun loadArtwork(imageView: ImageView, data: Any, options: List<ArtworkImageLoader.Options>, onCompletion: ((Result<Unit>) -> Unit)?, onColorSetGenerated: ((ColorSet) -> Unit)?) {
        val glideRequest = getRequestBuilder(options)

        onCompletion?.let {
            glideRequest.addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    onCompletion(Result.failure(e?.cause ?: Exception("Failed to load image")))
                    return false
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    onCompletion(Result.success(Unit))
                    return false
                }
            })
        }

        glideRequest
            .load(data)
            .into(imageView)
    }

    override fun loadBitmap(data: Any, width: Int, height: Int, options: List<ArtworkImageLoader.Options>, onCompletion: (Bitmap?) -> Unit) {
        loadBitmapTarget(data, options, completionHandler = onCompletion)
            .submit(width, height)
    }

    override fun loadBitmap(data: Any): ByteArray? {
        return try {
            requestManager
                .`as`(ByteArray::class.java)
                .load(data)
                .submit()
                .get()
        } catch (e: InterruptedException) {
            return null
        } catch (e: ExecutionException) {
            return null
        } catch (e: CancellationException) {
            return null
        }
    }

    override fun loadColorSet(data: Any, callback: (ColorSet?) -> Unit) {
        requestManager
            .`as`(ColorSet::class.java)
            .load(data)
            .addListener(object : RequestListener<ColorSet> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<ColorSet>?, isFirstResource: Boolean): Boolean {
                    callback(null)
                    return true
                }

                override fun onResourceReady(resource: ColorSet?, model: Any?, target: Target<ColorSet>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    callback(resource)
                    return true
                }
            })
            .submit(256, 256)
    }

    private fun loadBitmapTarget(data: Any, options: List<ArtworkImageLoader.Options>, completionHandler: (Bitmap?) -> Unit): RequestBuilder<Bitmap> {
        val glideRequest = requestManager
            .asBitmap()
            .load(data)
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                    completionHandler(null)
                    return true
                }

                override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    completionHandler(resource)
                    return true
                }
            })

        options.forEach { option ->
            when (option) {
                is ArtworkImageLoader.Options.CircleCrop -> {
                    glideRequest.apply(RequestOptions.circleCropTransform())
                }
                is ArtworkImageLoader.Options.RoundedCorners -> {
                    glideRequest.apply(RequestOptions.bitmapTransform(MultiTransformation(mutableListOf(CenterCrop(), RoundedCorners(option.radius)))))
                }
                is ArtworkImageLoader.Options.Priority -> {
                    when (option) {
                        ArtworkImageLoader.Options.Priority.Low -> glideRequest.priority(Priority.LOW)
                        ArtworkImageLoader.Options.Priority.Default -> glideRequest.priority(Priority.NORMAL)
                        ArtworkImageLoader.Options.Priority.High -> glideRequest.priority(Priority.HIGH)
                        ArtworkImageLoader.Options.Priority.Max -> glideRequest.priority(Priority.IMMEDIATE)
                    }
                }
                is ArtworkImageLoader.Options.Crossfade -> {
                    throw NotImplementedError()
                }
                is ArtworkImageLoader.Options.CenterCrop -> {
                    glideRequest.apply(RequestOptions.centerCropTransform())
                }
            }
        }

        return glideRequest
    }

    @DrawableRes
    var placeHolderResId: Int = R.drawable.ic_placeholder_album_rounded

    fun getRequestBuilder(options: List<ArtworkImageLoader.Options>): RequestBuilder<Drawable> {
        val glideRequest = requestManager
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
                    when (option) {
                        ArtworkImageLoader.Options.Priority.Low -> glideRequest.priority(Priority.LOW)
                        ArtworkImageLoader.Options.Priority.Default -> glideRequest.priority(Priority.NORMAL)
                        ArtworkImageLoader.Options.Priority.High -> glideRequest.priority(Priority.HIGH)
                        ArtworkImageLoader.Options.Priority.Max -> glideRequest.priority(Priority.IMMEDIATE)
                    }
                }
                is ArtworkImageLoader.Options.Crossfade -> {
                    glideRequest.transition(DrawableTransitionOptions.withCrossFade(option.duration))
                }
                is ArtworkImageLoader.Options.Placeholder -> {
                    glideRequest.placeholder(option.placeholderResId)
                }
                is ArtworkImageLoader.Options.CenterCrop -> {
                    glideRequest.apply(RequestOptions.centerCropTransform())
                }
            }
        }

        return glideRequest
    }

    override fun clear(imageView: ImageView) {
        requestManager.clear(imageView)
    }

    fun clear(target: Target<Bitmap>) {
        requestManager.clear(target)
    }

    override suspend fun clearCache(context: Context?) {
        context?.let {
            Glide.get(context).clearMemory()
        }
        withContext(Dispatchers.IO) {
            context?.let {
                Glide.get(context).clearDiskCache()
            }
        }
    }
}