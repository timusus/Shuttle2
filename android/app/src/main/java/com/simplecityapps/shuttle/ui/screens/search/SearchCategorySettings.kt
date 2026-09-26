package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

/** The result categories search was last left showing. */
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
        }
    }
}

/** Saves the result categories search shows. */
class SaveSearchCategories @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(categories: Set<SearchCategory>) {
        with(preferenceManager) {
            searchFilterArtists = SearchCategory.Artists in categories
            searchFilterAlbums = SearchCategory.Albums in categories
            searchFilterSongs = SearchCategory.Songs in categories
            searchFilterGenres = SearchCategory.Genres in categories
            searchFilterPlaylists = SearchCategory.Playlists in categories
        }
    }
}
