package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import javax.inject.Inject

class UpdatePlaylistSortOrder @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: Playlist, sortOrder: PlaylistSongSortOrder, sortDescending: Boolean) = playlistRepository.updatePlaylistSortOder(playlist, sortOrder, sortDescending)
}
