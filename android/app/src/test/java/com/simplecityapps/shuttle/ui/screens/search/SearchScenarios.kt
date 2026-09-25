package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery

object SearchScenarios {
    val radiohead = createAlbumArtist("Radiohead", albumCount = 9)
    val okComputer = createAlbum("OK Computer", "Radiohead", year = 1997)
    val kidA = createAlbum("Kid A", "Radiohead", year = 2000)
    val songs = listOf(
        createSong(id = 1, name = "Airbag", albumArtist = "Radiohead", album = "OK Computer"),
        createSong(id = 2, name = "Paranoid Android", albumArtist = "Radiohead", album = "OK Computer"),
        createSong(id = 3, name = "Everything In Its Right Place", albumArtist = "Radiohead", album = "Kid A"),
    )
    val genre = createGenre("Alternative", songCount = 42)
    val playlist = createPlaylist(id = 7, name = "Radio favourites", songCount = 12)

    /** Hits for "radiohead", so rows bold what it matched. */
    private fun <T> hits(vararg items: T, query: String = "radiohead") = items.map { SearchHit(it, SearchQuery.parse(query)) }

    val start = SearchUiState(content = SearchContent.Recent(emptyList()))

    val recent = SearchUiState(content = SearchContent.Recent(listOf("radiohead", "massive attack", "bjork")))

    val results = SearchUiState(
        content = SearchContent.Results(
            query = "radiohead",
            results = SearchResults(
                artists = hits(radiohead),
                albums = hits(okComputer, kidA),
                songs = hits(*songs.toTypedArray()),
                genres = hits(genre),
                playlists = hits(playlist),
                top = SearchCategory.Artists,
            ),
        ),
    )

    val songsOnly = SearchUiState(
        categories = setOf(SearchCategory.Songs),
        content = SearchContent.Results("radiohead", SearchResults(songs = hits(*songs.toTypedArray()), top = SearchCategory.Songs)),
    )

    /** More songs than the section shows before "See all", beside an artist so it isn't the only section. */
    val manySongs = SearchUiState(
        content = SearchContent.Results(
            query = "radiohead",
            results = SearchResults(
                artists = hits(radiohead),
                songs = hits(*(1..8).map { createSong(id = it.toLong(), name = "Track $it", albumArtist = "Radiohead", album = "Demos") }.toTypedArray()),
                top = SearchCategory.Artists,
            ),
        ),
    )

    val noResults = SearchUiState(content = SearchContent.NoResults("zzzz"))
}
