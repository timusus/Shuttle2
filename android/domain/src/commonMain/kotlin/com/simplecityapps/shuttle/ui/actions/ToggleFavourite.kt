package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** Makes [song] a favourite, or stops it being one when [isFavourite] is already true: the player's heart. */
@Inject
class ToggleFavourite(
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(song: Song, isFavourite: Boolean) {
        songRepository.setFavourite(listOf(song), favourite = !isFavourite)
    }
}
