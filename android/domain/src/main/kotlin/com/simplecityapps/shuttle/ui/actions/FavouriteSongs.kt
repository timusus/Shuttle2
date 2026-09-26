package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * Makes a selection's songs favourites ([favourite] true: "Add to Favorites"), or stops them being ones ([favourite]
 * false: "Remove from Favorites"). A song that's already a favourite keeps its place in the Favourites list.
 */
class FavouriteSongs @Inject constructor(
    private val songRepository: SongRepository,
    private val resolveSongs: ResolveSongs,
) {
    /** @return the songs changed, for the Undo: once removed, the Favourites list no longer resolves to them. */
    suspend operator fun invoke(selection: MediaSelection, favourite: Boolean = true): List<Song> {
        val songs = resolveSongs(selection)
        if (songs.isNotEmpty()) songRepository.setFavourite(songs, favourite)
        return songs
    }
}
