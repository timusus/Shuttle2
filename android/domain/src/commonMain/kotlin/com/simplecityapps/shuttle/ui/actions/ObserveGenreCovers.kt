package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The songs whose covers make up each of [genres]' artwork, by genre name: up to [ObservePlaylistCovers.CoverCount]
 * songs from different albums, by album artist and album ([GenreRepository.getGenreCoverSongs]), for the same 2x2
 * mosaic a playlist draws (#643). Re-emits as the library changes.
 */
@Inject
class ObserveGenreCovers(
    private val genreRepository: GenreRepository,
) {
    operator fun invoke(genres: List<Genre>): Flow<Map<String, List<Song>>> {
        if (genres.isEmpty()) return flowOf(emptyMap())
        return combine(
            genres.map { genre ->
                genreRepository.getGenreCoverSongs(genre.name, ObservePlaylistCovers.CoverCount).map { songs -> genre.name to songs }
            },
        ) { covers -> covers.toMap() }
    }
}
