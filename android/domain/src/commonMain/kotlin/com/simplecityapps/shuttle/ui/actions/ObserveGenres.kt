package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Genre
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** The genres matching [GenreQuery], the whole library by default; re-emits as they change. With Offline mode on, genres with no song that can play are left out. */
@Inject
class ObserveGenres(
    private val genreRepository: GenreRepository,
    private val filterOfflineGroups: FilterOfflineGroups,
) {
    operator fun invoke(query: GenreQuery = GenreQuery.All()): Flow<List<Genre>> = filterOfflineGroups.genres(genreRepository.getGenres(query))
}
