package com.simplecityapps.imageloading.coil

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.target
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.toBitmap
import coil3.transform.Transformation
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.Inject
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoilArtworkImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val sessionArtworkCache: LruCache<String, Bitmap>
) : ArtworkImageLoader {
    override fun loadBitmap(
        data: Any,
        width: Int,
        height: Int,
        options: List<ArtworkImageLoader.Options>,
        onCompletion: (Bitmap?) -> Unit
    ): ArtworkImageLoader.Request {
        val request =
            bitmapRequest(data)
                .size(width, height)
                .apply {
                    if (ArtworkImageLoader.Options.CenterCrop in options) {
                        scale(Scale.FILL)
                        transformations(CenterCropTransformation)
                    }
                }
                .target(
                    onSuccess = { image -> onCompletion(image.toBitmap()) },
                    onError = { onCompletion(null) }
                )
                .build()
        val disposable = imageLoader.enqueue(request)
        return ArtworkImageLoader.Request { disposable.dispose() }
    }

    override suspend fun loadBitmap(data: Any): ByteArray? {
        val result = imageLoader.execute(bitmapRequest(data).size(Size.ORIGINAL).build()) as? SuccessResult ?: return null
        return withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { output ->
                result.image.toBitmap().compress(Bitmap.CompressFormat.JPEG, 100, output)
                output.toByteArray()
            }
        }
    }

    override suspend fun clearCache() {
        // The media session's bitmaps are kept apart from Coil's, and would otherwise outlive the clear
        synchronized(sessionArtworkCache) { sessionArtworkCache.evictAll() }
        imageLoader.memoryCache?.clear()
        withContext(Dispatchers.IO) { imageLoader.diskCache?.clear() }
    }

    // These bitmaps are drawn into notifications, widgets and Cast artwork, or compressed, none of which take a hardware bitmap
    private fun bitmapRequest(data: Any): ImageRequest.Builder = ImageRequest.Builder(context)
        .data(data)
        .allowHardware(false)
}

/** Scales the image to cover the requested size and crops the overflow, centred. */
private object CenterCropTransformation : Transformation() {
    override val cacheKey: String = "CenterCropTransformation"

    override suspend fun transform(
        input: Bitmap,
        size: Size
    ): Bitmap = ThumbnailUtils.extractThumbnail(input, size.width.pxOrElse { input.width }, size.height.pxOrElse { input.height })
}
