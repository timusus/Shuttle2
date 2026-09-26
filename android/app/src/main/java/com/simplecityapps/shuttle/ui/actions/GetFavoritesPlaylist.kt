package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import javax.inject.Inject

/** The Favorites playlist, created on first use if it doesn't exist yet. */
class GetFavoritesPlaylist @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(): Playlist = playlistRepository.getFavoritesPlaylist()
}
