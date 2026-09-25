package com.simplecityapps.shuttle.ui.screens.search

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.GenreRow
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.S2FilterChip
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SearchNoResults
import com.simplecityapps.shuttle.designsystem.component.SearchRecentRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork

/** What the user can do on the Search screen; the destination wires each to the ViewModel, navigator or actions host. */
class SearchCallbacks(
    val onSearch: () -> Unit,
    val onToggleCategory: (SearchCategory) -> Unit,
    val onRemoveRecentSearch: (String) -> Unit,
    val onSongClick: (index: Int) -> Unit,
    val onAlbumClick: (Album) -> Unit,
    val onArtistClick: (AlbumArtist) -> Unit,
    val onGenreClick: (Genre) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onShowActions: (MediaActionsTarget) -> Unit,
)

/**
 * The Search destination (redesign inventory, section 2): a search field over filter chips, then the recent searches
 * while the field is empty, or the results grouped by type. [queryState] holds the field's text; the destination
 * feeds it to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    queryState: TextFieldState,
    callbacks: SearchCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Arriving with nothing typed means the user wants to type.
    LaunchedEffect(Unit) { if (queryState.text.isEmpty()) focusRequester.requestFocus() }

    Column(modifier.fillMaxSize()) {
        Surface(
            shape = SearchBarDefaults.inputFieldShape,
            color = SearchBarDefaults.colors().containerColor,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            SearchBarDefaults.InputField(
                state = queryState,
                onSearch = {
                    callbacks.onSearch()
                    focusManager.clearFocus()
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (queryState.text.isNotEmpty()) {
                    { S2IconButton(Icons.Rounded.Close, stringResource(com.simplecityapps.shuttle.designsystem.R.string.ds_clear_search), { queryState.clearText() }) }
                } else {
                    null
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SearchCategory.entries.forEach { category ->
                S2FilterChip(
                    label = category.label(),
                    selected = category in uiState.categories,
                    onClick = { callbacks.onToggleCategory(category) },
                )
            }
        }
        when (val content = uiState.content) {
            is SearchContent.Recent -> RecentSearches(content.searches, onSelect = { queryState.edit { replace(0, length, it) } }, onRemove = callbacks.onRemoveRecentSearch)
            SearchContent.Searching -> LoadingState(Modifier.fillMaxSize())
            is SearchContent.NoResults -> SearchNoResults(content.query, Modifier.fillMaxSize())
            is SearchContent.Results -> SearchResultList(content.results, callbacks)
        }
    }
}

@Composable
private fun RecentSearches(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (searches.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.search_start_title),
            message = stringResource(R.string.search_start_message),
            icon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "header") { SectionHeader(stringResource(R.string.search_recent)) }
        searches.forEach { query ->
            item(key = "recent:$query") { SearchRecentRow(query, onClick = { onSelect(query) }, onRemove = { onRemove(query) }) }
        }
    }
}

@Composable
private fun SearchResultList(
    results: SearchResults,
    callbacks: SearchCallbacks,
) {
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    // Precomputed here because LazyListScope isn't composable.
    val artistSummaries = results.artists.map { countString(R.plurals.albumsPlural, it.albumCount) }
    val genreSummaries = results.genres.map { countString(R.plurals.songsPlural, it.songCount) }
    val playlistSummaries = results.playlists.map { countString(R.plurals.songsPlural, it.songCount) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        section(R.string.search_category_artists, "artist", results.artists, { it.groupKey }) { index, artist ->
            val name = artist.name ?: artist.friendlyArtistName ?: unknown
            ArtistRow(
                name = name,
                summary = artistSummaries[index],
                onClick = { callbacks.onArtistClick(artist) },
                artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, size = ArtworkSize.Small, shape = ArtworkShape.Circle) },
                onLongClick = { callbacks.onShowActions(MediaActionsTarget(name, null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist)) },
                onMore = { callbacks.onShowActions(MediaActionsTarget(name, null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist)) },
            )
        }
        section(R.string.search_category_albums, "album", results.albums, { it.groupKey ?: it.name }) { _, album ->
            val title = album.name ?: unknown
            val artist = album.albumArtist ?: album.friendlyArtistName ?: unknown
            val target = MediaActionsTarget(title, artist, MediaSelection.Albums(album), ArtworkPlaceholder.Album)
            AlbumRow(
                title = title,
                artist = artist,
                meta = album.year?.toString(),
                onClick = { callbacks.onAlbumClick(album) },
                artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Small) },
                onLongClick = { callbacks.onShowActions(target) },
                onMore = { callbacks.onShowActions(target) },
            )
        }
        section(R.string.search_category_songs, "song", results.songs, { it.id }) { index, song ->
            val title = song.name ?: unknown
            val subtitle = song.subtitle(unknown)
            val target = MediaActionsTarget(title, subtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)
            SongRow(
                title = title,
                subtitle = subtitle,
                onClick = { callbacks.onSongClick(index) },
                artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
                onLongClick = { callbacks.onShowActions(target) },
                onMore = { callbacks.onShowActions(target) },
            )
        }
        section(R.string.search_category_genres, "genre", results.genres, { it.name }) { index, genre ->
            val target = MediaActionsTarget(genre.name, genreSummaries[index], MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)
            GenreRow(
                name = genre.name,
                songCount = genreSummaries[index],
                onClick = { callbacks.onGenreClick(genre) },
                onLongClick = { callbacks.onShowActions(target) },
                onMore = { callbacks.onShowActions(target) },
            )
        }
        section(R.string.search_category_playlists, "playlist", results.playlists, { it.id }) { index, playlist ->
            val target = MediaActionsTarget(playlist.name, playlistSummaries[index], MediaSelection.Playlists(playlist), ArtworkPlaceholder.Playlist)
            PlaylistRow(
                name = playlist.name,
                summary = playlistSummaries[index],
                onClick = { callbacks.onPlaylistClick(playlist) },
                onLongClick = { callbacks.onShowActions(target) },
                onMore = { callbacks.onShowActions(target) },
            )
        }
    }
}

/** One result group: a header, then a row per item. Keys are prefixed with [type] so groups can't collide. */
private fun <T> LazyListScope.section(
    @StringRes title: Int,
    type: String,
    items: List<T>,
    key: (T) -> Any?,
    row: @Composable (index: Int, item: T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header:$type", contentType = "header") { SectionHeader(stringResource(title)) }
    itemsIndexed(items, key = { _, item -> "$type:${key(item)}" }, contentType = { _, _ -> type }) { index, item -> row(index, item) }
}

private fun Song.subtitle(unknown: String): String = listOfNotNull(friendlyArtistName ?: albumArtist, album).joinToString(" · ").ifEmpty { unknown }

@Composable
private fun countString(@PluralsRes plural: Int, count: Int): String = pluralStringResource(plural, count, count).replace("{count}", count.toString())

@Composable
internal fun SearchCategory.label(): String = stringResource(
    when (this) {
        SearchCategory.Artists -> R.string.search_category_artists
        SearchCategory.Albums -> R.string.search_category_albums
        SearchCategory.Songs -> R.string.search_category_songs
        SearchCategory.Genres -> R.string.search_category_genres
        SearchCategory.Playlists -> R.string.search_category_playlists
    },
)
