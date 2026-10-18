package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.workRunOf
import dev.zacsweers.metro.Inject

/**
 * Adds a selection's songs to the queue after the last movement of the work playing now, so "play next" never lands
 * mid-symphony. Outside a work it does nothing: the action isn't offered there.
 */
@Inject
class PlayAfterWork(
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    private val resolveSongs: ResolveSongs,
    private val filterPlayableSongs: FilterPlayableSongs,
) {
    /** @return the songs added; empty if the selection had none or the current song isn't in a work. */
    suspend operator fun invoke(selection: MediaSelection): List<Song> {
        val songs = filterPlayableSongs(resolveSongs(selection))
        if (songs.isEmpty()) return songs
        val before = queueOperations.queueStateFlow.value
        val current = before.currentPosition ?: return emptyList()
        val run = workRunOf(before.items.map { it.song }, current) ?: return emptyList()
        val runUids = run.map { before.items[it].uid }.toSet()
        val beforeUids = before.items.mapTo(HashSet()) { it.uid }
        playbackOperations.playNext(songs)
        // The song can advance while playNext builds the items, so locate everything by uid in the queue as it is now.
        val items = queueOperations.queueStateFlow.value.items
        val inserted = items.indices.filter { items[it].uid !in beforeUids }
        val end = items.indexOfLast { it.uid in runUids }
        val first = inserted.firstOrNull() ?: return songs
        // Carry the inserted songs, in order, to the end of the run; if playback already moved past it they are there.
        if (end > first) repeat(inserted.size) { queueOperations.move(first, end) }
        return songs
    }

    companion object {
        fun offered(queue: List<Song>, currentPosition: Int?): Boolean = currentPosition != null && workRunOf(queue, currentPosition) != null
    }
}
