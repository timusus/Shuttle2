package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import javax.inject.Inject

class ReorderPlaylistSongs @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist, playlistSongs: List<PlaylistSong>) = playlistRepository.updatePlaylistSongsSortOder(playlist, playlistSongs)
}
