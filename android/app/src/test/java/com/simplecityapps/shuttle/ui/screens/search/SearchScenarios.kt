package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong

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

    val start = SearchUiState(content = SearchContent.Recent(emptyList()))

    val recent = SearchUiState(content = SearchContent.Recent(listOf("radiohead", "massive attack", "bjork")))

    val results = SearchUiState(
        content = SearchContent.Results(
            query = "radiohead",
            results = SearchResults(
                artists = listOf(radiohead),
                albums = listOf(okComputer, kidA),
                songs = songs,
                genres = listOf(genre),
                playlists = listOf(playlist),
            ),
        ),
    )

    val songsOnly = SearchUiState(
        categories = setOf(SearchCategory.Songs),
        content = SearchContent.Results("radiohead", SearchResults(songs = songs)),
    )

    val noResults = SearchUiState(content = SearchContent.NoResults("zzzz"))
}
