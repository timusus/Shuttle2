package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/**
 * One song's lyrics, or null when it has none: the ones stored with the song, else [onlineLyrics]. Songs don't carry
 * them (#873), so a screen that shows lyrics loads them here.
 */
@Inject
class LoadLyrics(
    private val songRepository: SongRepository,
    private val onlineLyrics: OnlineLyricsSource,
) {
    /** Emits when looking online is turned on or off, which can change what [invoke] finds for the same song. */
    val onlineEnabled: Flow<Boolean> get() = onlineLyrics.enabled

    suspend operator fun invoke(song: Song): String? = songRepository.loadLyrics(song.id) ?: onlineLyrics.lookup(song)
}
