package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Adds a selection's songs to the queue, after the current song ([Position.Next]) or at the end ([Position.End]). */
class EnqueueSongs @Inject constructor(
    private val playbackManager: PlaybackOperations,
    private val resolveSongs: ResolveSongs,
) {
    enum class Position { Next, End }

    /** @return the songs added; empty if the selection had none. */
    suspend operator fun invoke(selection: MediaSelection, position: Position): List<Song> {
        val songs = resolveSongs(selection)
        if (songs.isEmpty()) return songs
        when (position) {
            Position.Next -> playbackManager.playNext(songs)
            Position.End -> playbackManager.addToQueue(songs)
        }
        return songs
    }
}
