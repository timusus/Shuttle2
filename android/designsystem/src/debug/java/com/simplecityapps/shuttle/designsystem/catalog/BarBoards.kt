package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2SearchBar
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.SearchInputField
import com.simplecityapps.shuttle.designsystem.component.SearchNoResults
import com.simplecityapps.shuttle.designsystem.component.SearchRecentRow
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow

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
                    title = "OK Computer",
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
                    SearchRecentRow("radiohead", {}, {})
                    SearchRecentRow("massive attack", {}, {})
                }
            },
            BoardSection("Typing: results") {
                SearchViewFrame("radio") {
                    SectionHeader("Artists", containerColor = SearchBarDefaults.colors().containerColor)
                    ArtistRow("Radiohead", {}, summary = "9 albums", artwork = { Artwork(ArtworkPlaceholder.Artist, shape = ArtworkShape.Circle, image = { SampleArt(2) }) })
                    SectionHeader("Songs", containerColor = SearchBarDefaults.colors().containerColor)
                    SongRow("Radio Friendly Unit Shifter", "Nirvana · In Utero", {}, artwork = { Artwork(ArtworkPlaceholder.Song, image = { SampleArt() }) }, duration = "4:51")
                }
            },
            BoardSection("No results") { SearchViewFrame("zzxq") { SearchNoResults("zzxq") } },
        ),
    )
}
