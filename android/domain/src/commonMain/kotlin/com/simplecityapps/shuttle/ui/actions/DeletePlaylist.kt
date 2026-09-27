package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import dev.zacsweers.metro.Inject

@Inject
class DeletePlaylist(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist) = playlistRepository.deletePlaylist(playlist)
}
