package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

/**
 * The type chips a search is narrowed to. Empty is the All chip, and so is every type at once, which is how All is saved
 * (and how a search that never narrowed was left).
 */
internal fun Set<SearchCategory>.asFilter(): Set<SearchCategory> = if (size == SearchCategory.entries.size) emptySet() else this

/** The type chips search was last left on; empty for All. */
class ReadSearchCategories @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(): Set<SearchCategory> = with(preferenceManager) {
        buildSet {
            if (searchFilterArtists) add(SearchCategory.Artists)
            if (searchFilterAlbums) add(SearchCategory.Albums)
            if (searchFilterSongs) add(SearchCategory.Songs)
            if (searchFilterGenres) add(SearchCategory.Genres)
            if (searchFilterPlaylists) add(SearchCategory.Playlists)
        }.asFilter()
    }
}

/** Saves the type chips search is narrowed to; empty for All. */
class SaveSearchCategories @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(filter: Set<SearchCategory>) {
        val categories = filter.ifEmpty { SearchCategory.entries.toSet() }
        with(preferenceManager) {
            searchFilterArtists = SearchCategory.Artists in categories
            searchFilterAlbums = SearchCategory.Albums in categories
            searchFilterSongs = SearchCategory.Songs in categories
            searchFilterGenres = SearchCategory.Genres in categories
            searchFilterPlaylists = SearchCategory.Playlists in categories
        }
    }
}
