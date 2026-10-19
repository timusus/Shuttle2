package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget

/** What the user did on the Search screen; [onEvent] hands each to its callback. */
sealed interface SearchEvent {
    data object Search : SearchEvent

    data object SelectAll : SearchEvent

    data class ToggleCategory(val category: SearchCategory) : SearchEvent

    data class SelectRecent(val query: String) : SearchEvent

    data class RemoveRecent(val query: String) : SearchEvent

    /** Queues the song results from [index]. */
    data class PlaySongs(val index: Int) : SearchEvent

    data class OpenAlbum(val album: Album) : SearchEvent

    data class OpenArtist(val artist: AlbumArtist) : SearchEvent

    data class OpenGenre(val genre: Genre) : SearchEvent

    data class OpenPlaylist(val playlist: Playlist) : SearchEvent

    data class ShowActions(val target: MediaActionsTarget) : SearchEvent

    data class Play(val selection: MediaSelection) : SearchEvent
}

/** Runs [event]'s callback; a recent search goes to [fillQuery], since the field's text is the screen's own. */
fun SearchCallbacks.onEvent(event: SearchEvent, fillQuery: (String) -> Unit) {
    when (event) {
        SearchEvent.Search -> onSearch()
        SearchEvent.SelectAll -> onSelectAll()
        is SearchEvent.ToggleCategory -> onToggleCategory(event.category)
        is SearchEvent.SelectRecent -> fillQuery(event.query)
        is SearchEvent.RemoveRecent -> onRemoveRecentSearch(event.query)
        is SearchEvent.PlaySongs -> onSongClick(event.index)
        is SearchEvent.OpenAlbum -> onAlbumClick(event.album)
        is SearchEvent.OpenArtist -> onArtistClick(event.artist)
        is SearchEvent.OpenGenre -> onGenreClick(event.genre)
        is SearchEvent.OpenPlaylist -> onPlaylistClick(event.playlist)
        is SearchEvent.ShowActions -> onShowActions(event.target)
        is SearchEvent.Play -> onPlay(event.selection)
    }
}

/** The chip's tap: All clears the categories, any other toggles its own. */
fun SearchViewState.Chip.tapEvent(): SearchEvent = category?.let(SearchEvent::ToggleCategory) ?: SearchEvent.SelectAll

/** A result's tap: a song plays the song results from its place among them; anything else opens. */
fun SearchResults.tapEvent(category: SearchCategory, index: Int): SearchEvent = when (category) {
    SearchCategory.Artists -> SearchEvent.OpenArtist(artists[index].item)
    SearchCategory.Albums -> SearchEvent.OpenAlbum(albums[index].item)
    SearchCategory.Songs -> SearchEvent.PlaySongs(index)
    SearchCategory.Genres -> SearchEvent.OpenGenre(genres[index].item)
    SearchCategory.Playlists -> SearchEvent.OpenPlaylist(playlists[index].item)
}

/** The top result's Play: the top song plays like a tap on it, from the head of the songs; anything else plays itself. */
fun SearchResults.topPlayEvent(category: SearchCategory): SearchEvent = if (category == SearchCategory.Songs) SearchEvent.PlaySongs(0) else SearchEvent.Play(selection(category, 0))
