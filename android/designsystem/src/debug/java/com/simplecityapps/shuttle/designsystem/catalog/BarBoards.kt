package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2NavItem
import com.simplecityapps.shuttle.designsystem.component.S2NavigationBar
import com.simplecityapps.shuttle.designsystem.component.S2NavigationRail
import com.simplecityapps.shuttle.designsystem.component.S2SearchBar
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SearchInputField
import com.simplecityapps.shuttle.designsystem.component.SearchNoResults
import com.simplecityapps.shuttle.designsystem.component.SearchRecentRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.fixtures.SampleLibrary

@Composable
private fun LibraryActions() {
    S2IconButton(Icons.Rounded.Search, "Search", {})
    S2IconButton(Icons.Rounded.MoreVert, "More options", {})
}

/** The large bar with its scroll state pushed to the collapsed end, as a scrolled list leaves it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsedLargeTopBar() {
    val state = rememberTopAppBarState()
    LaunchedEffect(state) {
        withFrameNanos { }
        state.heightOffset = state.heightOffsetLimit
    }
    S2LargeTopBar(
        title = "Library",
        subtitle = "3,310 songs",
        actions = { LibraryActions() },
        scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state),
    )
}

@Composable
fun TopBarBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Large flexible, expanded: title, subtitle, actions") {
                S2LargeTopBar(title = "Library", subtitle = "3,310 songs", actions = { LibraryActions() })
            },
            BoardSection("Large flexible, collapsed") { CollapsedLargeTopBar() },
            BoardSection("Large flexible, title only, back") { S2LargeTopBar(title = "Settings", onBack = {}) },
            BoardSection("Detail (pinned), back and actions") {
                S2TopBar(
                    title = SampleLibrary.album("harbour-weather").title,
                    onBack = {},
                    actions = {
                        S2IconButton(Icons.Rounded.Shuffle, "Shuffle", {})
                        S2IconButton(Icons.Rounded.MoreVert, "More options", {})
                    },
                )
            },
            BoardSection("Long title") {
                S2LargeTopBar(title = "A playlist name long enough to run out of room", subtitle = "1,204 songs", onBack = {}, actions = { LibraryActions() })
            },
        ),
    )
}

/**
 * The expanded search view as `ExpandedFullScreenSearchBar` lays it out: the input field, a
 * divider, then the content, on the search container colour. The real one opens in a dialog
 * window, which a board capture can't see, so the board draws its layout in place.
 */
@Composable
private fun SearchViewFrame(query: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = SearchBarDefaults.colors()
    Surface(color = colors.containerColor) {
        Column {
            SearchInputField(
                state = rememberSearchBarState(initialValue = SearchBarValue.Expanded),
                textFieldState = rememberTextFieldState(query),
                onSearch = {},
                placeholder = "Search your library",
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = colors.dividerColor)
            content()
        }
    }
}

@Composable
fun SearchBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Collapsed") {
                S2SearchBar(
                    state = rememberSearchBarState(),
                    textFieldState = rememberTextFieldState(),
                    onSearch = {},
                    placeholder = "Search your library",
                    modifier = Modifier.fillMaxWidth(),
                ) {}
            },
            BoardSection("Focused, empty: recent searches") {
                SearchViewFrame("") {
                    SearchRecentRow("juniper static", {}, {})
                    SearchRecentRow("blue hours", {}, {})
                }
            },
            BoardSection("Typing: results") {
                SearchViewFrame("salt") {
                    SectionHeader("Artists", containerColor = SearchBarDefaults.colors().containerColor)
                    val artist = SampleLibrary.artist("Saltmarsh Choir")
                    ArtistRow(artist.name, {}, summary = "${artist.albums.size} albums", artwork = { Artwork(ArtworkPlaceholder.Artist, shape = ArtworkShape.Circle, image = { SampleArt(artist.coverAlbumId) }) })
                    SectionHeader("Songs", containerColor = SearchBarDefaults.colors().containerColor)
                    val song = SampleLibrary.songs.first { it.title == "Wall of Salt" }
                    SongRow(song.title, "${song.artist} · ${song.album}", {}, artwork = { Artwork(ArtworkPlaceholder.Song, image = { SampleArt(song.albumId) }) }, duration = song.duration)
                }
            },
            BoardSection("No results") { SearchViewFrame("zzxq") { SearchNoResults("zzxq") } },
        ),
    )
}

internal fun navItems(playlistBadge: String? = null, searchBadge: String? = null) = listOf(
    S2NavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    S2NavItem("Library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    S2NavItem("Playlists", Icons.AutoMirrored.Outlined.QueueMusic, Icons.Rounded.QueueMusic, badge = playlistBadge),
    S2NavItem("Search", Icons.Rounded.Search, badge = searchBadge),
)

@Composable
fun NavBarBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("4 items, Home selected") { S2NavigationBar(navItems(), 0, {}) },
            BoardSection("4 items, Library selected") { S2NavigationBar(navItems(), 1, {}) },
            BoardSection("Badges: dot on Playlists, count on Search") { S2NavigationBar(navItems(playlistBadge = "", searchBadge = "3"), 2, {}) },
            BoardSection("3 items (Search in the top bar)") { S2NavigationBar(navItems().take(3), 0, {}) },
            BoardSection("3 items, long count badge") { S2NavigationBar(navItems(playlistBadge = "999+").take(3), 2, {}) },
        ),
    )
}

@Composable
private fun Rail(expanded: Boolean, selectedIndex: Int, playlistBadge: String? = null) {
    S2NavigationRail(
        items = navItems(playlistBadge = playlistBadge),
        selectedIndex = selectedIndex,
        onSelect = {},
        state = rememberWideNavigationRailState(if (expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed),
        modifier = Modifier.height(620.dp),
    )
}

/** Each rail at a fixed height beside its sibling, as the shell lays it against the content pane. */
@Composable
fun NavRailBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Collapsed: Library selected; badged; Search selected") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rail(expanded = false, selectedIndex = 1)
                    Rail(expanded = false, selectedIndex = 2, playlistBadge = "3")
                    Rail(expanded = false, selectedIndex = 3)
                }
            },
            BoardSection("Expanded: Home selected, badged") { Rail(expanded = true, selectedIndex = 0, playlistBadge = "") },
        ),
    )
}
