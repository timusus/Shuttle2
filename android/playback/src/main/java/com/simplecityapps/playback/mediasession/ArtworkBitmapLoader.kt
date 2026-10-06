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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.imageloading.coil.NowPlayingArtwork
import com.simplecityapps.imageloading.coil.nowPlayingArtwork
import com.simplecityapps.playback.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.ArtworkSettings

/**
 * Loads the current song's artwork, through the app's image loader, for the notification and the session's metadata
 * (the lock screen, Android Auto, a Bluetooth head unit). Queue items carry no artwork URI, as an S2 artwork key isn't a
 * URI another app could open, so the song comes from [currentSong]: the session asks only for the current item's.
 *
 * With "Now playing artwork" set to the artist image, it's the song's album artist's image the loader asks for, else the song's own. A song
 * with no artwork gets a placeholder; with media session artwork turned off in settings there's none at all.
 */
@UnstableApi
class ArtworkBitmapLoader(
    private val context: Context,
    private val artworkImageLoader: ArtworkImageLoader,
    private val artworkCache: LruCache<String, Bitmap>,
    private val artworkSettings: ArtworkSettings,
    private val currentSong: () -> Song?
) : BitmapLoader {
    private val placeholder: Bitmap by lazy {
        drawableToBitmap(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_music_note_black_24dp, context.theme)!!)
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (!artworkSettings.mediaSessionArtwork.value) return null
        val song = currentSong() ?: return null
        // The song, or its album artist's image falling back to the song's, when the setting asks for it (#952)
        val artwork = nowPlayingArtwork(song, artworkSettings.nowPlayingArtwork.value)
        val key = sessionArtworkKey(artwork, artworkSettings.localOnly.value, artworkSettings.wifiOnly.value)
        synchronized(artworkCache) { artworkCache[key] }?.let { cached -> return Futures.immediateFuture(cached) }

        val future = SettableFuture.create<Bitmap>()
        val request = artworkImageLoader.loadBitmap(data = artwork.model, width = ARTWORK_SIZE, height = ARTWORK_SIZE) { image ->
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

    internal companion object {
        const val ARTWORK_SIZE = 512

        /**
         * The session cache's key for [artwork]: what it resolves to, plus the artwork settings that decide whether a remote image
         * is reachable, so a bitmap loaded (or fallen back to the song's own art) under one setting isn't served under another.
         * The Clear artwork cache setting empties the cache itself.
         */
        fun sessionArtworkKey(
            artwork: NowPlayingArtwork,
            localOnly: Boolean,
            wifiOnly: Boolean
        ): String = "${artwork.cacheKey}_${ARTWORK_SIZE}_${ARTWORK_SIZE}|local=$localOnly|wifi=$wifiOnly"

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
