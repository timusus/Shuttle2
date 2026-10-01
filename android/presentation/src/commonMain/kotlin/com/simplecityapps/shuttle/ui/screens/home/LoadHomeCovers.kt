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

/**
 * The songs whose covers make up each playlist and genre tile's 2x2 mosaic on Home (#633, #646), by [HomeItem.key]:
 * up to [ObservePlaylistCovers.CoverCount] songs from different albums (one per album identity), in the playlist's order
 * or, for a genre, by album artist and album; only the covers' songs are read whole, every tile's side by side. An item with none is
 * left out, and draws its generated artwork. Loaded once per set of sections: the sections reload as the library changes, and the covers with them.
 */
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
