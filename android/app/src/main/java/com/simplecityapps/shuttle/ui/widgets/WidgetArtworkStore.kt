package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.getArtworkCacheKey
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Widget artwork lives on disk, one file per song artwork shared by every widget, so the widget state only
 * carries a path. Files are rendered once at the largest size any widget layout draws, so they're sharp at
 * every layout, capped so a tablet doesn't send the launcher a huge bitmap. [WidgetManager] calls it from one
 * coroutine at a time, but the bookkeeping is a concurrent set so a stray caller can't corrupt it.
 */
@Singleton
class WidgetArtworkStore
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ArtworkImageLoader
) {
    private val directory: File get() = File(context.filesDir, "widget_artwork")

    private val sizePx: Int
        get() = (maxWidgetArtSize.value * context.resources.displayMetrics.density).toInt().coerceAtMost(MAX_SIZE_PX)

    fun file(song: Song): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(song.getArtworkCacheKey(sizePx, sizePx).toByteArray())
        return File(directory, digest.joinToString("", postfix = ".jpg") { "%02x".format(it) })
    }

    /** Songs whose artwork failed to load, so play/pause updates don't retry them. Reset by [prune]. */
    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    /** The saved artwork for [song], loading and saving it first if needed. Null when the song has no art. */
    suspend fun artworkPath(song: Song): String? {
        val file = file(song)
        if (file.name in missing) return null
        if (withContext(Dispatchers.IO) { file.exists() }) return file.path
        val bitmap = loadBitmap(song)
        if (bitmap == null) {
            missing += file.name
            return null
        }
        return withContext(Dispatchers.IO) { save(bitmap, file) }?.path
    }

    /** Deletes every saved file except those for [keep]. */
    suspend fun prune(keep: Collection<Song>) {
        val keepNames = keep.map { file(it).name }.toSet()
        missing.retainAll(keepNames)
        withContext(Dispatchers.IO) {
            directory.listFiles()?.filter { it.name !in keepNames }?.forEach { it.delete() }
        }
    }

    private suspend fun loadBitmap(song: Song): Bitmap? = suspendCancellableCoroutine { continuation ->
        val request =
            imageLoader.loadBitmap(song, sizePx, sizePx, listOf(ArtworkImageLoader.Options.CenterCrop)) { bitmap ->
                if (continuation.isActive) continuation.resume(bitmap)
            }
        // When WidgetManager stops waiting, stop the load too rather than leave it running unobserved.
        continuation.invokeOnCancellation { request.cancel() }
    }

    private fun save(
        bitmap: Bitmap,
        file: File
    ): File? {
        // Hardware bitmaps can't be compressed directly.
        val source =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
        directory.mkdirs()
        // Write to a temporary file and rename, so a widget never reads a half-written image.
        val temp = File(directory, "${file.name}.tmp")
        return try {
            temp.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (temp.renameTo(file)) file else null
        } catch (e: IOException) {
            Timber.e(e, "Failed to save widget artwork")
            null
        } finally {
            temp.delete()
        }
    }

    private companion object {
        /** [maxWidgetArtSize] at 3x. Denser screens upscale a little rather than send the launcher a bigger bitmap. */
        const val MAX_SIZE_PX = 1260
    }
}
