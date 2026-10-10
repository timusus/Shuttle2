package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

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

    // Finishes the delete in progress even if the caller goes away: the system may already be showing its delete dialog,
    // and files the user deletes there must leave the library too. It just asks no further per-song confirmations.
    suspend operator fun invoke(selection: MediaSelection): Result {
        val caller = currentCoroutineContext()[Job]
        return withContext(NonCancellable) { delete(selection) { caller?.isActive != false } }
    }

    private suspend fun delete(
        selection: MediaSelection,
        callerActive: () -> Boolean,
    ): Result {
        val songs = resolveSongs(selection)
        val (mediaStoreSongs, otherSongs) = songs.filter { it.canBeDeleted() }.partition { it.mediaProvider == MediaProviderType.MediaStore }
        // MediaStore songs go to the deleter together, so the system can confirm the whole batch at once
        val mediaStoreDeleted = if (mediaStoreSongs.isEmpty()) emptySet() else mediaStoreDeleter.delete(mediaStoreSongs, callerActive)
        val deleted = mediaStoreSongs.filter { it in mediaStoreDeleted } + otherSongs.filter { fileDeleter.delete(it) }
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
 * Deletes MediaStore songs' files, which the app implements with the system's delete confirmation; the user may decline
 * it.
 */
fun interface MediaStoreSongDeleter {
    /**
     * Deletes song by song where the system confirms each one, stopping before the next song once [callerActive] is false.
     *
     * @return the songs whose files are gone: none if the user declined, fewer than [songs] if only some deleted.
     */
    suspend fun delete(
        songs: List<Song>,
        callerActive: () -> Boolean,
    ): Set<Song>
}

/** Deletes a song's file; the app implements it with the Storage Access Framework. */
fun interface SongFileDeleter {
    /** @return true if the file is gone. Runs off the main thread itself. */
    suspend fun delete(song: Song): Boolean
}
