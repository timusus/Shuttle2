package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.preview.toPlaylist
import com.simplecityapps.shuttle.ui.preview.toSong

/** Search over the sample library, so rows load the generated covers under `SampleArtworkGlide`. */
object SearchScenarios {
    /** What the sample library holds for "night": an artist, two albums, songs by name and by artist, and a playlist. */
    val nightjar = SampleLibrary.artist("Nightjar & the Loom").toAlbumArtist()
    val nightBus = SampleLibrary.album("night-bus-frequencies").toAlbum()
    val weatherSystems = SampleLibrary.album("weather-systems").toAlbum()
    val songs = listOf(
        SampleLibrary.album("lantern-hours").songs.first { it.title == "Night Ferry Lights" },
        SampleLibrary.album("night-bus-frequencies").songs[0],
        SampleLibrary.album("weather-systems").songs[0],
    ).map { it.toSong() }
    val playlist = SampleLibrary.playlist("Late Night").toPlaylist(id = 7)

    /** No sample genre is named for "night", so genres get their own query. */
    val genre = SampleLibrary.genres.first { it.name == "Jazz" }.toGenre()

    /** Hits for [query], so rows bold what it matched. */
    private fun <T> hits(vararg items: T, query: String = "night") = items.map { SearchHit(it, SearchQuery.parse(query)) }

    val start = SearchUiState(content = SearchContent.Recent(emptyList()))

    val recent = SearchUiState(content = SearchContent.Recent(listOf("nightjar", "harbour weather", "kestrel")))

    val results = SearchUiState(
        content = SearchContent.Results(
            query = "night",
            results = SearchResults(
                artists = hits(nightjar),
                albums = hits(nightBus, weatherSystems),
                songs = hits(*songs.toTypedArray()),
                playlists = hits(playlist),
                top = SearchCategory.Artists,
            ),
        ),
    )

    val genreResults = SearchUiState(
        content = SearchContent.Results("jazz", SearchResults(genres = hits(genre, query = "jazz"), top = SearchCategory.Genres)),
    )

    val songsOnly = SearchUiState(
        categories = setOf(SearchCategory.Songs),
        content = SearchContent.Results("night", SearchResults(songs = hits(*songs.toTypedArray()), top = SearchCategory.Songs)),
    )

    /** Juniper Static's first eight songs: more than the section shows before "See all". */
    val juniperSongs = SampleLibrary.artist("Juniper Static").albums.flatMap { it.songs }.take(8).map { it.toSong() }

    /** More songs than the section shows before "See all", beside an artist so it isn't the only section. */
    val manySongs = SearchUiState(
        content = SearchContent.Results(
            query = "juniper",
            results = SearchResults(
                artists = hits(SampleLibrary.artist("Juniper Static").toAlbumArtist(), query = "juniper"),
                songs = hits(*juniperSongs.toTypedArray(), query = "juniper"),
                top = SearchCategory.Artists,
            ),
        ),
    )

    val noResults = SearchUiState(content = SearchContent.NoResults("zzzz"))
}
