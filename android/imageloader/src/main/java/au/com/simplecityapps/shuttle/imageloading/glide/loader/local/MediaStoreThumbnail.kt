package au.com.simplecityapps.shuttle.imageloading.glide.loader.local

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.io.IOException
import java.io.InputStream
import timber.log.Timber

/**
 * Whether the directory loaders can see the images next to songs from these providers. A SAF tree grant covers every file,
 * but on Android 13+ the app holds only READ_MEDIA_AUDIO, and shared storage listings leave images out, so for MediaStore
 * songs the folder art comes from [openMediaStoreAudioThumbnail] instead.
 */
internal fun canListFolderImages(
    mediaProviders: Collection<MediaProviderType>,
    sharedStorageListsImages: Boolean
): Boolean = sharedStorageListsImages || mediaProviders.any { provider -> provider != MediaProviderType.MediaStore }

/** The song's MediaStore audio id, when it came from the MediaStore provider. */
internal fun Song.mediaStoreId(): Long? = if (mediaProvider == MediaProviderType.MediaStore) externalId?.toLongOrNull() else null

/**
 * MediaStore's thumbnail for an audio row: the song's embedded art, otherwise an image in its folder. MediaProvider builds it
 * with ThumbnailUtils.createAudioThumbnail, using its own storage access, so it finds folder art the app can't list.
 *
 * This is the call ContentResolver.loadThumbnail makes, left encoded so Glide decodes and caches it like other artwork.
 * MediaProvider sizes the thumbnail itself (half the display's shorter edge), whatever size is asked for.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal fun ContentResolver.openMediaStoreAudioThumbnail(mediaStoreId: Long): InputStream? {
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    val options = Bundle().apply { putParcelable(ContentResolver.EXTRA_SIZE, Point(THUMBNAIL_SIZE, THUMBNAIL_SIZE)) }
    return try {
        openTypedAssetFile(uri, "image/*", options, null)?.createInputStream()
    } catch (e: IOException) {
        // MediaProvider reports "no embedded or folder art" as FileNotFoundException
        Timber.v("No MediaStore thumbnail for $uri (${e.message})")
        null
    } catch (e: SecurityException) {
        Timber.v("Failed to retrieve MediaStore thumbnail (permission denial)")
        null
    } catch (e: IllegalArgumentException) {
        Timber.v("Failed to retrieve MediaStore thumbnail (${e.message})")
        null
    }
}

private const val THUMBNAIL_SIZE = 1024
