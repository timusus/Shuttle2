package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/** Only the covers' songs are read whole, so each Home reload stays cheap. */
class LoadHomeCovers @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val genreRepository: GenreRepository,
) {
    suspend operator fun invoke(sections: List<HomeSection>): Map<String, List<Song>> = coroutineScope {
        sections
            .flatMap { it.items }
            .distinctBy { it.key }
            .map { item ->
                async {
                    val covers = when (item) {
                        is HomeItem.PlaylistItem -> playlistRepository.getPlaylistCoverSongs(item.playlist, CoverCount).first()
                        is HomeItem.GenreItem -> genreRepository.getGenreCoverSongs(item.genre.name, CoverCount).first()
                        else -> emptyList()
                    }
                    covers.takeIf { it.isNotEmpty() }?.let { item.key to it }
                }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    private companion object {
        const val CoverCount = ObservePlaylistCovers.CoverCount
    }
}
