package com.simplecityapps.shuttle.ui.actions

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Deletes a selection's song files, then removes each deleted song from the library and the queue. Only songs that
 * [Song.canBeDeleted] are attempted; the rest are reported as failed. There's no undo, so callers confirm first.
 */
class DeleteSongs @Inject constructor(
    private val songRepository: SongRepository,
    private val queueManager: QueueOperations,
    private val resolveSongs: ResolveSongs,
    private val fileDeleter: SongFileDeleter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    data class Result(val deleted: List<Song>, val failed: List<Song>)

    suspend operator fun invoke(selection: MediaSelection): Result {
        val songs = resolveSongs(selection)
        val (deleted, failed) = withContext(ioDispatcher) {
            songs.partition { song -> song.canBeDeleted() && fileDeleter.delete(song) }
        }
        deleted.forEach { songRepository.remove(it) }
        if (deleted.isNotEmpty()) {
            val ids = deleted.mapTo(mutableSetOf()) { it.id }
            queueManager.remove(queueManager.getQueue().filter { it.song.id in ids })
        }
        return Result(deleted, failed)
    }
}

/** Deletes a song's file. */
fun interface SongFileDeleter {
    /** @return true if the file is gone. */
    fun delete(song: Song): Boolean
}

/** Deletes through the Storage Access Framework, the only way S2 is granted write access to a song's file. */
class SafSongFileDeleter @Inject constructor(
    @ApplicationContext private val context: Context,
) : SongFileDeleter {
    override fun delete(song: Song): Boolean = try {
        DocumentFile.fromSingleUri(context, song.path.toUri())?.delete() == true
    } catch (e: Exception) {
        Timber.e(e, "Failed to delete ${song.path}")
        false
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SongFileDeleterModule {
    @Binds
    abstract fun bindSongFileDeleter(deleter: SafSongFileDeleter): SongFileDeleter
}
