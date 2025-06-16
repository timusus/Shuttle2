package com.simplecityapps.app

import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import junit.framework.TestCase.assertEquals
import org.junit.Test

class GenreComparatorTest {
    private val mediaProviders = listOf(MediaProviderType.MediaStore)

    @Test
    fun sortsByName() {
        val genres = listOf(
            Genre(name = "Pop", 1, 10, mediaProviders),
            Genre(name = "Ambient", 2, 10, mediaProviders),
            Genre(name = "Metal", 3, 10, mediaProviders)
        )

        val sortedGenres = genres.sortedWith(GenreSortOrder.Name.comparator)

        assertEquals(
            listOf(
                Genre(name = "Ambient", 2, 10, mediaProviders),
                Genre(name = "Metal", 3, 10, mediaProviders),
                Genre(name = "Pop", 1, 10, mediaProviders)
            ),
            sortedGenres
        )
    }

    @Test
    fun sortsBySongCount() {
        val genres = listOf(
            Genre(name = "Pop", 1, 10, mediaProviders),
            Genre(name = "Ambient", 2, 10, mediaProviders),
            Genre(name = "Metal", 3, 10, mediaProviders)
        )

        val sortedGenres = genres.sortedWith(GenreSortOrder.SongCount.comparator)

        assertEquals(
            listOf(
                Genre(name = "Metal", 3, 10, mediaProviders),
                Genre(name = "Ambient", 2, 10, mediaProviders),
                Genre(name = "Pop", 1, 10, mediaProviders)
            ),
            sortedGenres
        )
    }
}
