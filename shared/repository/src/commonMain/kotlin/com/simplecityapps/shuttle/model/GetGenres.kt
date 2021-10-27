package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.repository.GenreRepository
import kotlinx.coroutines.flow.Flow

class GetGenres @Inject constructor(
    private val genreRepository: GenreRepository
) {

    operator fun invoke(): Flow<List<Genre>> {
        return genreRepository.genres
    }
}