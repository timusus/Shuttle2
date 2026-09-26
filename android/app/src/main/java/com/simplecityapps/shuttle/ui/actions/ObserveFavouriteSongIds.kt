package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** The ids of the songs in the Favorites playlist; empty (rather than an error) without one. */
class ObserveFavouriteSongIds @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    operator fun invoke(): Flow<Set<Long>> = flow { emit(playlistRepository.getFavoritesPlaylist()) }
        .flatMapLatest { playlistRepository.getSongsForPlaylist(it) }
        .map { songs -> songs.map { it.song.id }.toSet() }
        .onStart { emit(emptySet()) }
        .catch { emit(emptySet()) }
}
