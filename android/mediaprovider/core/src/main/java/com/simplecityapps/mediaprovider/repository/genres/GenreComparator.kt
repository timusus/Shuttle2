package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder

val GenreSortOrder.comparator: Comparator<Genre>
    get() {
        return when (this) {
            GenreSortOrder.Default -> GenreComparator.defaultComparator
            GenreSortOrder.SongCount -> GenreComparator.songCountComparator
        }
    }

object GenreComparator {
    val defaultComparator: Comparator<Genre> by lazy { compareBy { genre -> genre.name } }

    val songCountComparator: Comparator<Genre> by lazy {
        compareByDescending<Genre> { genre -> genre.songCount }.then(defaultComparator)
    }
}
