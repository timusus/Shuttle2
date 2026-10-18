package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Up to [ObservePlaylistCovers.CoverCount] songs per genre, from different albums, for the 2x2 mosaic. */
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
