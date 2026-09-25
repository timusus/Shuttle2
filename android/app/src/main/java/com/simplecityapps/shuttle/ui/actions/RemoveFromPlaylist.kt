package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Takes entries out of a playlist ([invoke]) and puts them back where they were ([restore], the Undo).
 */
class RemoveFromPlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist, entries: List<PlaylistSong>) {
        playlistRepository.removeFromPlaylist(playlist, entries)
    }

    /**
     * Re-adds [removed] and restores the order [before] had. The repository appends re-added songs with fresh entry
     * ids, so each current entry is matched back to its old position by song; entries added since go last.
     */
    suspend fun restore(playlist: Playlist, removed: List<PlaylistSong>, before: List<PlaylistSong>) {
        playlistRepository.addToPlaylist(playlist, removed.map { it.song })
        val current = playlistRepository.getSongsForPlaylist(playlist).first().sortedBy { it.sortOrder }
        val positions = before.sortedBy { it.sortOrder }
            .withIndex()
            .groupBy({ it.value.song.id }, { it.index })
            .mapValues { (_, indices) -> ArrayDeque(indices) }
        val ordered = current
            .map { entry -> entry to (positions[entry.song.id]?.removeFirstOrNull() ?: Int.MAX_VALUE) }
            .sortedBy { (_, position) -> position }
            .mapIndexed { index, (entry, _) -> entry.copy(sortOrder = index.toLong()) }
        playlistRepository.updatePlaylistSongsSortOder(playlist, ordered)
    }
}
