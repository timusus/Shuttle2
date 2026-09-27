package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import kotlinx.coroutines.flow.Flow

interface GenreRepository {
    fun getGenres(query: GenreQuery): Flow<List<Genre>>

    fun getSongsForGenres(
        genres: List<String>,
        songQuery: SongQuery
    ): Flow<List<Song>>

    fun getSongsForGenre(
        genre: String,
        songQuery: SongQuery
    ): Flow<List<Song>> = getSongsForGenres(listOf(genre), songQuery)
}
