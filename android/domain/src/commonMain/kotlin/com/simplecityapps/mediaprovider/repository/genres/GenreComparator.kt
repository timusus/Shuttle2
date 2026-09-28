package com.simplecityapps.mediaprovider.repository.genres

import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.CollationStrength
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.localeCollator

val GenreSortOrder.comparator: Comparator<Genre>
    get() {
        return when (this) {
            GenreSortOrder.Default -> GenreComparator.defaultComparator
            GenreSortOrder.SongCount -> GenreComparator.songCountComparator
        }
    }

object GenreComparator {
    private val collator by lazy { localeCollator(CollationStrength.Tertiary) }

    // Collated like the other library lists, so "alpha" sorts before "Beta" and each letter's genres stay together.
    val defaultComparator: Comparator<Genre> by lazy { compareBy(collator) { genre -> genre.name } }

    val songCountComparator: Comparator<Genre> by lazy {
        compareByDescending<Genre> { genre -> genre.songCount }.then(defaultComparator)
    }
}
