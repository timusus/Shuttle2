package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListUiState
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.text.stringResource as stringResourceKey

/** Playlists: the smart playlists, Favorites first, pinned above the user's, with "New playlist" in their header. */
@Composable
fun PlaylistsPage(
    state: PlaylistListUiState,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit,
    onNewPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        PlaylistListUiState.LoadingState.Loading -> LibraryContentState.Loading

        PlaylistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning

        // Smart playlists are always there, so an empty list still shows them and "New playlist".
        PlaylistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.playlist_list_empty), modifier, state.scanProgress, controls, hasItems = true) {
        val listState = rememberLazyListState()
        ScrollToTopOnReselect(ShellTab.Library, listState)
        val headerCount = controlsItemCount(controls) + 1 + state.smartPlaylists.size + 1
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-playlists")) {
                controlsItem(controls)
                item(key = "smart-header") { SectionHeader(title = stringResource(R.string.library_smart_playlists)) }
                items(state.smartPlaylists, key = { "smart-${it.id}" }) { smartPlaylist ->
                    PlaylistRow(
                        name = stringResourceKey(smartPlaylist.id.nameKey),
                        onClick = { onSmartPlaylistClick(smartPlaylist) },
                        artwork = { LibraryArtwork(null, smartPlaylist.placeholder) },
                    )
                }
                item(key = "playlists-header") {
                    SectionHeader(
                        title = stringResource(R.string.library_playlists),
                        action = stringResource(R.string.playlist_menu_create_playlist),
                        onAction = onNewPlaylist,
                    )
                }
                if (state.playlists.isEmpty()) {
                    item(key = "playlists-empty") {
                        S2Text(
                            text = stringResource(R.string.playlist_list_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = S2Spacing.large, vertical = S2Spacing.smallMedium).testTag("library-playlists-empty"),
                        )
                    }
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        name = playlist.name,
                        summary = pluralString(R.plurals.songsPlural, playlist.songCount),
                        onClick = { onPlaylistClick(playlist) },
                        onMore = { onPlaylistMore(playlist) },
                        artwork = { CoverMosaic(state.covers[playlist.id].orEmpty(), ArtworkPlaceholder.Playlist) },
                    )
                }
            }
            FastScroller(modifier = fastScrollerModifier(controls), getPopupText = { index -> state.playlists.getOrNull(index - headerCount)?.name?.firstOrNull()?.uppercase() }, state = listState)
        }
    }
}
