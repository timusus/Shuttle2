package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createGenre
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import io.kotest.matchers.shouldBe
import org.junit.Test

class SearchEventsTest {
    private val query = SearchQuery.parse("x")

    private fun <T> hits(vararg items: T) = items.map { SearchHit(it, query) }

    private val artist = createAlbumArtist(name = "Nightjar")
    private val album = createAlbum("Weather Systems", "Nightjar")
    private val songs = listOf(createSong(id = 1, name = "One"), createSong(id = 2, name = "Two"))
    private val genre = createGenre(name = "Jazz")
    private val playlist = createPlaylist(name = "Late Night")
    private val results = SearchResults(
        artists = hits(artist),
        albums = hits(album),
        songs = hits(*songs.toTypedArray()),
        genres = hits(genre),
        playlists = hits(playlist),
    )

    /** Each callback's calls, in order, as "name(argument)". */
    private val calls = mutableListOf<String>()
    private val filled = mutableListOf<String>()
    private val callbacks = SearchCallbacks(
        onSearch = { calls += "search" },
        onSelectAll = { calls += "selectAll" },
        onToggleCategory = { calls += "toggle($it)" },
        onRemoveRecentSearch = { calls += "removeRecent($it)" },
        onSongClick = { calls += "song($it)" },
        onAlbumClick = { calls += "album(${it.name})" },
        onArtistClick = { calls += "artist(${it.name})" },
        onGenreClick = { calls += "genre(${it.name})" },
        onPlaylistClick = { calls += "playlist(${it.name})" },
        onShowActions = { calls += "actions(${it.title})" },
        onPlay = { calls += "play($it)" },
    )

    private fun send(event: SearchEvent) = callbacks.onEvent(event) { filled += it }

    @Test
    fun `each event runs its own callback`() {
        send(SearchEvent.Search)
        send(SearchEvent.SelectAll)
        send(SearchEvent.ToggleCategory(SearchCategory.Albums))
        send(SearchEvent.RemoveRecent("nightjar"))
        send(SearchEvent.PlaySongs(1))
        send(SearchEvent.OpenAlbum(album))
        send(SearchEvent.OpenArtist(artist))
        send(SearchEvent.OpenGenre(genre))
        send(SearchEvent.OpenPlaylist(playlist))
        send(SearchEvent.ShowActions(MediaActionsTarget("Nightjar", null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.entries.first())))
        send(SearchEvent.Play(MediaSelection.Albums(album)))

        calls shouldBe listOf(
            "search",
            "selectAll",
            "toggle(Albums)",
            "removeRecent(nightjar)",
            "song(1)",
            "album(Weather Systems)",
            "artist(Nightjar)",
            "genre(Jazz)",
            "playlist(Late Night)",
            "actions(Nightjar)",
            "play(${MediaSelection.Albums(album)})",
        )
        filled shouldBe emptyList()
    }

    @Test
    fun `a recent search fills the field and runs no callback`() {
        send(SearchEvent.SelectRecent("harbour weather"))

        filled shouldBe listOf("harbour weather")
        calls shouldBe emptyList()
    }

    @Test
    fun `the All chip selects all and a category chip toggles its category`() {
        SearchViewState.Chip(category = null, selected = false).tapEvent() shouldBe SearchEvent.SelectAll
        SearchViewState.Chip(category = SearchCategory.Albums, selected = false).tapEvent() shouldBe SearchEvent.ToggleCategory(SearchCategory.Albums)
    }

    @Test
    fun `tapping a song plays the songs from its index`() {
        results.tapEvent(SearchCategory.Songs, 1) shouldBe SearchEvent.PlaySongs(1)
    }

    @Test
    fun `tapping any other result opens it`() {
        results.tapEvent(SearchCategory.Artists, 0) shouldBe SearchEvent.OpenArtist(artist)
        results.tapEvent(SearchCategory.Albums, 0) shouldBe SearchEvent.OpenAlbum(album)
        results.tapEvent(SearchCategory.Genres, 0) shouldBe SearchEvent.OpenGenre(genre)
        results.tapEvent(SearchCategory.Playlists, 0) shouldBe SearchEvent.OpenPlaylist(playlist)
    }

    @Test
    fun `the top song's Play plays the songs from index 0`() {
        results.topPlayEvent(SearchCategory.Songs) shouldBe SearchEvent.PlaySongs(0)
    }

    @Test
    fun `the top result's Play plays its selection`() {
        results.topPlayEvent(SearchCategory.Artists) shouldBe SearchEvent.Play(MediaSelection.AlbumArtists(artist))
        results.topPlayEvent(SearchCategory.Albums) shouldBe SearchEvent.Play(MediaSelection.Albums(album))
    }
}
