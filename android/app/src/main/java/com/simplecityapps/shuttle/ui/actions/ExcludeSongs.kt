package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * Hides a selection's songs from the library and drops them from the queue ([excluded] true), or brings them back
 * ([excluded] false, the Undo). Undo doesn't put songs back in the queue.
 */
class ExcludeSongs @Inject constructor(
    private val songRepository: SongRepository,
    private val queueManager: QueueOperations,
    private val resolveSongs: ResolveSongs,
) {
    /**
     * @return the songs changed. Keep them for the Undo: once excluded, an album or artist no longer resolves to them.
     */
    suspend operator fun invoke(selection: MediaSelection, excluded: Boolean = true): List<Song> {
        val songs = resolveSongs(selection)
        if (songs.isEmpty()) return songs
        songRepository.setExcluded(songs, excluded)
        if (excluded) {
            val ids = songs.mapTo(mutableSetOf()) { it.id }
            queueManager.remove(queueManager.getQueue().filter { it.song.id in ids })
        }
        return songs
    }
}
