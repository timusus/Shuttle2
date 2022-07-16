package com.simplecityapps.shuttle.model

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.repository.GenreRepository
import com.simplecityapps.shuttle.repository.SongRepository
import kotlinx.coroutines.flow.Flow

class GetSongs @Inject constructor(
    private val songRepository: SongRepository
) {

    operator fun invoke(): Flow<List<Song>> {
        return songRepository.songs
    }
}