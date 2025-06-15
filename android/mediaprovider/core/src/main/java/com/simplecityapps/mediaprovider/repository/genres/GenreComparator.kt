package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder

val GenreSortOrder.comparator: Comparator<Genre>
    get() = when (this) {
        GenreSortOrder.Default -> GenreComparator.defaultComparator
        GenreSortOrder.Name -> GenreComparator.nameComparator
        GenreSortOrder.SongCount -> GenreComparator.songCountComparator
    }

object GenreComparator {
    val defaultComparator: Comparator<Genre> = compareBy { it.name }

    val nameComparator: Comparator<Genre> = compareBy { it.name }

    val songCountComparator: Comparator<Genre> = compareByDescending<Genre> { it.songCount }
        .then(defaultComparator)
}
