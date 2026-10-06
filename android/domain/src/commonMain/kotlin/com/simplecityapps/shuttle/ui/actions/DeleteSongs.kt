package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Deletes a selection's song files, then removes each deleted song from the library and the queue. Only songs that
 * [Song.canBeDeleted] are attempted; the rest are reported as failed. There's no undo, so callers confirm first.
 */
@Inject
class DeleteSongs(
    private val songRepository: SongRepository,
    private val queueOperations: QueueOperations,
    private val resolveSongs: ResolveSongs,
    private val fileDeleter: SongFileDeleter,
    private val mediaStoreDeleter: MediaStoreSongDeleter,
) {
    data class Result(val deleted: List<Song>, val failed: List<Song>)

    suspend operator fun invoke(selection: MediaSelection): Result {
        val songs = resolveSongs(selection)
        val (mediaStoreSongs, otherSongs) = songs.filter { it.canBeDeleted() }.partition { it.mediaProvider == MediaProviderType.MediaStore }
        // One system confirmation covers every MediaStore song, so they're deleted together
        val mediaStoreDeleted = if (mediaStoreSongs.isNotEmpty() && mediaStoreDeleter.delete(mediaStoreSongs)) mediaStoreSongs else emptyList()
        val deleted = mediaStoreDeleted + otherSongs.filter { fileDeleter.delete(it) }
        val failed = songs.filter { it !in deleted }
        deleted.forEach { songRepository.remove(it) }
        if (deleted.isNotEmpty()) {
            val ids = deleted.mapTo(mutableSetOf()) { it.id }
            queueOperations.remove(queueOperations.getQueue().filter { it.song.id in ids })
        }
        return Result(deleted, failed)
    }
}

/**
 * Deletes MediaStore songs' files in one request, which the app implements with the system's delete confirmation; the
 * user may decline it.
 */
fun interface MediaStoreSongDeleter {
    /** @return true if every file is gone, false if the user declined or the delete failed. */
    suspend fun delete(songs: List<Song>): Boolean
}

/** Deletes a song's file; the app implements it with the Storage Access Framework. */
fun interface SongFileDeleter {
    /** @return true if the file is gone. Runs off the main thread itself. */
    suspend fun delete(song: Song): Boolean
}
