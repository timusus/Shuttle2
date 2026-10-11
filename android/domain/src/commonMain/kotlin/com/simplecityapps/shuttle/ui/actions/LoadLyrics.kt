package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * One song's lyrics, or null when it has none: the ones stored with the song, else [onlineLyrics]'. Songs don't carry
 * them (#873), so a screen that shows lyrics loads them here.
 */
@Inject
class LoadLyrics(
    private val songRepository: SongRepository,
    private val onlineLyrics: OnlineLyricsSource,
) {
    suspend operator fun invoke(song: Song): String? = songRepository.loadLyrics(song.id) ?: onlineLyrics.lookup(song)
}
