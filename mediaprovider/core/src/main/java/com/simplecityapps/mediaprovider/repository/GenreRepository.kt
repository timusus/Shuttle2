package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

interface GenreRepository {
    fun getGenres(query: GenreQuery): Flow<List<Genre>>
    fun getSongsForGenre(genre: String, songQuery: SongQuery): Flow<List<Song>>
}

sealed class GenreQuery(
    val predicate: ((Genre) -> Boolean),
    val sortOrder: GenreSortOrder = GenreSortOrder.Default
) {
    class All(sortOrder: GenreSortOrder = GenreSortOrder.Default) : GenreQuery(
        predicate = { true },
        sortOrder = sortOrder
    )

    class GenreName(val genreName: String) : GenreQuery(
        predicate = { genre -> genre.name == genreName },
        sortOrder = GenreSortOrder.Default
    )
}

enum class GenreSortOrder : Serializable {
    Default;

    val comparator: Comparator<Genre>
        get() {
            return when (this) {
                Default -> compareBy { genre -> genre.name }
            }
        }
}