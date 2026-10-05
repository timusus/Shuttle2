package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import dev.zacsweers.metro.Inject

/** One song's lyrics, or null when it has none. Songs don't carry them (#873), so a screen that shows lyrics loads them here. */
@Inject
class LoadLyrics(
    private val songRepository: SongRepository,
) {
    suspend operator fun invoke(songId: Long): String? = songRepository.loadLyrics(songId)
}
