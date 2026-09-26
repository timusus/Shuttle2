package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Adds [song] to the Favorites playlist, or removes it when [isFavourite] is already true. */
class ToggleFavourite @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(song: Song, isFavourite: Boolean) {
        val favourites = playlistRepository.getFavoritesPlaylist()
        if (isFavourite) {
            playlistRepository.removeSongsFromPlaylist(favourites, listOf(song))
        } else {
            playlistRepository.addToPlaylist(favourites, listOf(song))
        }
    }
}
