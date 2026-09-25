package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.shuttle.model.Genre
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The genres matching [GenreQuery], the whole library by default; re-emits as they change. */
class ObserveGenres @Inject constructor(
    private val genreRepository: GenreRepository,
) {
    operator fun invoke(query: GenreQuery = GenreQuery.All()): Flow<List<Genre>> = genreRepository.getGenres(query)
}
