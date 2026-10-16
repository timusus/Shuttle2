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
        val state = queueOperations.queueStateFlow.value
        val current = state.currentPosition ?: return emptyList()
        val run = workRunOf(state.items.map { it.song }, current) ?: return emptyList()
        playbackOperations.playNext(songs)
        // The songs now sit right after the current one; carry them to the end of the run, in order.
        val target = run.last + songs.size
        repeat(songs.size) { queueOperations.move(current + 1, target) }
        return songs
    }

    companion object {
        /** Whether the current song is in a work of the queue. */
        fun offered(queue: List<Song>, currentPosition: Int?): Boolean = currentPosition != null && workRunOf(queue, currentPosition) != null
    }
}
