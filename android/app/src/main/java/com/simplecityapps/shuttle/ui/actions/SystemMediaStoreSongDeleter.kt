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

    private val flow = MediaStoreDeleteFlow(
        edge = object : MediaStoreDeleteEdge {
            override fun uriFor(externalId: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, externalId)

            @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
            override fun createDeleteRequest(uris: List<Uri>): IntentSender = MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

            override fun hasWriteAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

            override suspend fun deleteDirect(uri: Uri): DirectDelete = withContext(ioDispatcher) {
                try {
                    if (context.contentResolver.delete(uri, null, null) > 0) DirectDelete.Deleted else DirectDelete.Failed
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        DirectDelete.NeedsConfirmation(e.userAction.actionIntent.intentSender)
                    } else {
                        Timber.e(e, "Not permitted to delete $uri")
                        DirectDelete.Failed
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete $uri")
                    DirectDelete.Failed
                }
            }
        },
        sdkInt = Build.VERSION.SDK_INT,
        confirmations = confirmations,
    )

    override suspend fun delete(
        songs: List<Song>,
        callerActive: () -> Boolean,
    ): Set<Song> = flow.delete(songs, callerActive)
}

internal sealed interface DirectDelete {
    data object Deleted : DirectDelete

    data object Failed : DirectDelete

    /** API 29: the user can grant this one delete through [intentSender]. */
    data class NeedsConfirmation(val intentSender: IntentSender) : DirectDelete
}

/** The platform calls behind [MediaStoreDeleteFlow]. */
internal interface MediaStoreDeleteEdge {
    fun uriFor(externalId: Long): Uri

    fun createDeleteRequest(uris: List<Uri>): IntentSender

    fun hasWriteAccess(): Boolean

    suspend fun deleteDirect(uri: Uri): DirectDelete
}

/** Maps a batch of songs onto the API level's delete mechanism; see [SystemMediaStoreSongDeleter]. */
internal class MediaStoreDeleteFlow(
    private val edge: MediaStoreDeleteEdge,
    private val sdkInt: Int,
    private val confirmations: ConfirmationHandoff<IntentSender>,
) {
    suspend fun delete(
        songs: List<Song>,
        callerActive: () -> Boolean,
    ): Set<Song> {
        // A song without a valid MediaStore row isn't deleted, but doesn't hold up the rest
        val uris = songs.mapNotNull { song -> song.externalId?.toLongOrNull()?.let { song to edge.uriFor(it) } }
        if (uris.isEmpty()) return emptySet()
        if (sdkInt >= Build.VERSION_CODES.R) {
            // All or nothing: the user accepts or declines the whole batch
            val accepted = try {
                confirmations.confirm(edge.createDeleteRequest(uris.map { it.second }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete ${uris.size} MediaStore songs")
                false
            }
            return if (accepted) uris.mapTo(mutableSetOf()) { it.first } else emptySet()
        }
        if (sdkInt <= Build.VERSION_CODES.P && !edge.hasWriteAccess()) {
            Timber.w("Not deleting ${uris.size} MediaStore songs without write access to storage")
            return emptySet()
        }
        val deleted = mutableSetOf<Song>()
        for ((song, uri) in uris) {
            if (!callerActive()) break
            // A failed delete moves on to the next song; a declined prompt ends the batch
            when (val result = edge.deleteDirect(uri)) {
                DirectDelete.Deleted -> deleted += song

                DirectDelete.Failed -> Unit

                is DirectDelete.NeedsConfirmation -> {
                    if (!confirmations.confirm(result.intentSender)) break
                    if (edge.deleteDirect(uri) == DirectDelete.Deleted) deleted += song
                }
            }
        }
        return deleted
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
