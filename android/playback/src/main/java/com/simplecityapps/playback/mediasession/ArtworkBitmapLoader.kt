package com.simplecityapps.playback.mediasession

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.simplecityapps.playback.R
import com.simplecityapps.playback.getArtworkCacheKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager

/**
 * Loads the current song's artwork, through the app's image loader, for the notification and the session's metadata
 * (the lock screen, Android Auto, a Bluetooth head unit). Queue items carry no artwork URI, as an S2 artwork key isn't a
 * URI another app could open, so the song comes from [currentSong]: the session asks only for the current item's.
 *
 * A song with no artwork gets a placeholder; with media session artwork turned off in settings there's none at all.
 */
@UnstableApi
class ArtworkBitmapLoader(
    private val context: Context,
    private val artworkImageLoader: ArtworkImageLoader,
    private val artworkCache: LruCache<String, Bitmap?>,
    private val preferenceManager: GeneralPreferenceManager,
    private val currentSong: () -> Song?
) : BitmapLoader {
    private val placeholder: Bitmap by lazy {
        drawableToBitmap(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_music_note_black_24dp, context.theme)!!)
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (!preferenceManager.mediaSessionArtwork) return null
        val song = currentSong() ?: return null
        val key = song.getArtworkCacheKey(ARTWORK_SIZE, ARTWORK_SIZE)
        synchronized(artworkCache) { artworkCache[key] }?.let { cached -> return Futures.immediateFuture(cached) }

        val future = SettableFuture.create<Bitmap>()
        val request = artworkImageLoader.loadBitmap(data = song, width = ARTWORK_SIZE, height = ARTWORK_SIZE) { image ->
            if (image != null) {
                synchronized(artworkCache) { artworkCache.put(key, image) }
            }
            future.set(image ?: placeholder)
        }
        future.addListener({ if (future.isCancelled) request.cancel() }, Runnable::run)
        return future
    }

    override fun supportsMimeType(mimeType: String): Boolean = Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = BitmapFactory.decodeByteArray(data, 0, data.size)
        ?.let { bitmap -> Futures.immediateFuture(bitmap) }
        ?: Futures.immediateFailedFuture(IllegalArgumentException("Couldn't decode the artwork"))

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = Futures.immediateFailedFuture(UnsupportedOperationException("Artwork is loaded by song, not by URI"))

    private companion object {
        const val ARTWORK_SIZE = 512

        fun drawableToBitmap(drawable: Drawable): Bitmap {
            if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
            val bitmap =
                if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                    Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
                } else {
                    Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                }
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }
}
