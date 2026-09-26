package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.query.SongQuery
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Keeps the queued songs as the library has them: when a write replaces songs' metadata (a tag edit, a rescan, a
 * remote sync: [SongRepository.updatedSongIds]), gives the queued ones their stored data through
 * [QueueOperations.updateSongs], which keeps each item's place, and where playback is in the current one. A song removed
 * from the library stays queued as it was.
 *
 * Starts when it's created, and runs for the life of [scope].
 */
class QueueSongRefresher(
    private val songRepository: SongRepository,
    private val queue: QueueOperations,
    scope: CoroutineScope
) {
    init {
        scope.launch {
            songRepository.updatedSongIds.collect { songIds -> refresh(songIds) }
        }
    }

    private suspend fun refresh(songIds: Set<Long>) {
        val queued = queue.queueStateFlow.value.items.mapNotNullTo(mutableSetOf()) { item -> item.song.id.takeIf { it in songIds } }
        if (queued.isEmpty()) return
        try {
            queue.updateSongs(songRepository.getSongs(SongQuery.SongIds(queued.toList())).filterNotNull().first())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The queue keeps the songs as they were; the next update brings them up to date.
            Timber.e(e, "Failed to read the updated songs in the queue")
        }
    }
}
