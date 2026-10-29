package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Plays one copy of a merged song now, keeping the rest of the queue. The queued item is the chosen copy carrying the
 * song's copies with it first, so a later fallback still sees the others. When the song is already the current item,
 * the chosen copy takes its place rather than queueing a duplicate.
 */
@Inject
class PlayFromCopy(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    private val playSongs: PlaySongs,
) {
    suspend operator fun invoke(song: Song, copy: Song): PlaySongs.Result {
        val chosen = copy.copy(copies = listOf(copy) + song.allCopies.filter { it.id != copy.id })
        val before = queueOperations.queueStateFlow.value
        if (before.items.isEmpty()) return playSongs(listOf(chosen))

        // A queue with no current item is kept: the copy goes on its end.
        val current = before.currentItem
        if (current == null) playbackOperations.addToQueue(listOf(chosen)) else playbackOperations.playNext(listOf(chosen))

        // Found by identity: "next" is not the inserted item's slot while shuffled.
        val known = before.items.map { it.uid }.toSet()
        val position = queueOperations.queueStateFlow.value.items.indexOfFirst { it.uid !in known }
        if (position < 0) return PlaySongs.Result.Failure(null)

        playbackOperations.skipTo(position)
        if (current != null && current.song.id in song.allCopies.map { it.id }) playbackOperations.removeQueueItem(current)
        return PlaySongs.Result.Success
    }
}
