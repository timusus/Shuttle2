package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import javax.inject.Inject

/** Removes every song from [Playlist], keeping the playlist itself. */
class ClearPlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist) = playlistRepository.clearPlaylist(playlist)
}
