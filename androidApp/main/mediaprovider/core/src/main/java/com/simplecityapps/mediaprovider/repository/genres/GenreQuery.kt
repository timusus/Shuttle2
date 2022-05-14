package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder

sealed class GenreQuery(
    val predicate: (Genre) -> Boolean,
    val sortOrder: GenreSortOrder = GenreSortOrder.Default
) {
    class All(sortOrder: GenreSortOrder = GenreSortOrder.Default) : GenreQuery(
        predicate = { true },
        sortOrder = sortOrder
    )

    class GenreName(val genreName: String) : GenreQuery(
        predicate = { genre -> genre.name == genreName }
    )

    class Search(val query: String) : GenreQuery(
        predicate = { genre -> genre.name.contains(query, true) }
    )
}
