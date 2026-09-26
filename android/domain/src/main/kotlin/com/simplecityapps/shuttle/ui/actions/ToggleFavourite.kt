package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Makes [song] a favourite, or stops it being one when [isFavourite] is already true: the player's heart. */
class ToggleFavourite @Inject constructor(
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(song: Song, isFavourite: Boolean) {
        songRepository.setFavourite(listOf(song), favourite = !isFavourite)
    }
}
