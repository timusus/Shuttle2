package com.simplecityapps.shuttle.ui.actions

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Deletes MediaStore songs through the system: one `MediaStore.createDeleteRequest` confirmation for the whole batch on
 * API 30+, a `RecoverableSecurityException` confirmation per song on API 29, a direct delete (with write access to
 * storage) before that. The UI launches the confirmations it takes from [confirmations].
 */
@SingleIn(AppScope::class)
class SystemMediaStoreSongDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaStoreSongDeleter {
    val confirmations = ConfirmationHandoff<IntentSender>()

    override suspend fun delete(songs: List<Song>): Set<Song> {
        // A song without a valid MediaStore row isn't deleted, but doesn't hold up the rest
        val uris = songs.mapNotNull { song ->
            song.externalId?.toLongOrNull()?.let { song to ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
        }
        if (uris.isEmpty()) return emptySet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // All or nothing: the user accepts or declines the whole batch
            val accepted = try {
                confirmations.confirm(MediaStore.createDeleteRequest(context.contentResolver, uris.map { it.second }).intentSender)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete ${uris.size} MediaStore songs")
                false
            }
            return if (accepted) uris.mapTo(mutableSetOf()) { it.first } else emptySet()
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !hasWriteAccess()) {
            Timber.w("Not deleting ${uris.size} MediaStore songs without write access to storage")
            return emptySet()
        }
        return uris.filter { (_, uri) -> deleteLegacy(uri) }.mapTo(mutableSetOf()) { it.first }
    }

    private fun hasWriteAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Before API 30: a delete the app isn't permitted throws; on API 29 the exception carries the user's way out. */
    private suspend fun deleteLegacy(uri: Uri): Boolean {
        suspend fun deleteNow() = withContext(ioDispatcher) { context.contentResolver.delete(uri, null, null) > 0 }
        return try {
            try {
                deleteNow()
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    confirmations.confirm(e.userAction.actionIntent.intentSender) && deleteNow()
                } else {
                    Timber.e(e, "Not permitted to delete $uri")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete $uri")
            false
        }
    }
}

/** The media actions host launches the deleter's system dialogs, so it reaches it from the graph. */
@ContributesTo(AppScope::class)
interface MediaStoreDeleteEntryPoint {
    fun mediaStoreSongDeleter(): SystemMediaStoreSongDeleter
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class MediaStoreSongDeleterModule {
    @Binds
    abstract fun bindMediaStoreSongDeleter(deleter: SystemMediaStoreSongDeleter): MediaStoreSongDeleter
}
