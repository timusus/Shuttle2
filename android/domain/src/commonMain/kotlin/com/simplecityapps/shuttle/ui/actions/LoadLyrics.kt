package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** Songs don't carry lyrics, so screens load them here: the stored ones, else online. */
@Inject
class LoadLyrics(
    private val songRepository: SongRepository,
    private val onlineLyrics: OnlineLyricsSource,
) {
    /** Emits when looking online is turned on or off, which can change what [invoke] finds for the same song. */
    val onlineEnabled: Flow<Boolean> get() = onlineLyrics.enabled

    suspend operator fun invoke(song: Song): String? = songRepository.loadLyrics(song.id) ?: onlineLyrics.lookup(song)
}
