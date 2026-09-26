package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * Deletes a selection's song files, then removes each deleted song from the library and the queue. Only songs that
 * [Song.canBeDeleted] are attempted; the rest are reported as failed. There's no undo, so callers confirm first.
 */
class DeleteSongs @Inject constructor(
    private val songRepository: SongRepository,
    private val queueOperations: QueueOperations,
    private val resolveSongs: ResolveSongs,
    private val fileDeleter: SongFileDeleter,
) {
    data class Result(val deleted: List<Song>, val failed: List<Song>)

    suspend operator fun invoke(selection: MediaSelection): Result {
        val songs = resolveSongs(selection)
        val (deleted, failed) = songs.partition { song -> song.canBeDeleted() && fileDeleter.delete(song) }
        deleted.forEach { songRepository.remove(it) }
        if (deleted.isNotEmpty()) {
            val ids = deleted.mapTo(mutableSetOf()) { it.id }
            queueOperations.remove(queueOperations.getQueue().filter { it.song.id in ids })
        }
        return Result(deleted, failed)
    }
}

/** Deletes a song's file; the app implements it with the Storage Access Framework. */
fun interface SongFileDeleter {
    /** @return true if the file is gone. Runs off the main thread itself. */
    suspend fun delete(song: Song): Boolean
}
