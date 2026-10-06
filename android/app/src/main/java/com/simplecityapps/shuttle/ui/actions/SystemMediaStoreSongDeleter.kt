package com.simplecityapps.shuttle.ui.actions

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** A system dialog the UI has to launch for [SystemMediaStoreSongDeleter]; [result] completes true when the user accepts. */
class SystemDeleteRequest(val intentSender: IntentSender) {
    val result = CompletableDeferred<Boolean>()
}

/**
 * Deletes MediaStore songs through the system: one `MediaStore.createDeleteRequest` confirmation for the whole batch on
 * API 30+, a `RecoverableSecurityException` confirmation per song on API 29, a direct delete before that. The
 * confirmations are launched by the UI, which collects [requests].
 */
@SingleIn(AppScope::class)
class SystemMediaStoreSongDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaStoreSongDeleter {
    private val requestChannel = Channel<SystemDeleteRequest>(Channel.BUFFERED)

    val requests: Flow<SystemDeleteRequest> = requestChannel.receiveAsFlow()

    override suspend fun delete(songs: List<Song>): Boolean {
        val uris = songs.mapNotNull { song ->
            song.externalId?.toLongOrNull()?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
        }
        if (uris.size != songs.size) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                confirm(MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender)
            } else {
                uris.all { deleteLegacy(it) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete ${songs.size} MediaStore songs")
            false
        }
    }

    /** Before API 30: a delete the app isn't permitted throws; on API 29 the exception carries the user's way out. */
    private suspend fun deleteLegacy(uri: Uri): Boolean {
        fun deleteNow() = context.contentResolver.delete(uri, null, null) > 0
        return try {
            withContext(ioDispatcher) { deleteNow() }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                confirm(e.userAction.actionIntent.intentSender) && withContext(ioDispatcher) { deleteNow() }
            } else {
                Timber.e(e, "Not permitted to delete $uri")
                false
            }
        }
    }

    private suspend fun confirm(intentSender: IntentSender): Boolean {
        val request = SystemDeleteRequest(intentSender)
        requestChannel.send(request)
        return request.result.await()
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
