package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import dev.zacsweers.metro.Inject

/** Takes entries out of a playlist; [RestorePlaylistSongs] is the Undo. */
@Inject
class RemoveFromPlaylist(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist, entries: List<PlaylistSong>) {
        playlistRepository.removeFromPlaylist(playlist, entries)
    }
}
