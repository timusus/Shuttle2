package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder

val GenreSortOrder.comparator: Comparator<Genre>
    get() {
        return when (this) {
            GenreSortOrder.Default -> GenreComparator.defaultComparator
        }
    }

object GenreComparator {
    val defaultComparator: Comparator<Genre> by lazy { compareBy { genre -> genre.name } }
}