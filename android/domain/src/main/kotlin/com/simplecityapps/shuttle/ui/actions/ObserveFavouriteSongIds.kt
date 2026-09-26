package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart

/** The ids of the favourite songs; empty until they've loaded, and (rather than an error) if they can't be. */
class ObserveFavouriteSongIds @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(): Flow<Set<Long>> = songRepository.getFavouriteSongIds()
        .onStart { emit(emptySet()) }
        .catch { emit(emptySet()) }
}
