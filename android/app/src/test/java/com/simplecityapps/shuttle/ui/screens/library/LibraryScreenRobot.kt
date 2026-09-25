package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.folders.Folder
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListUiState
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListUiState
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState

/** The page states [LibraryScreenRobot] renders under each tab; a tab without one shows a stand-in label. */
data class LibraryPageStates(
    val songs: SongListUiState? = null,
    val albums: AlbumListUiState? = null,
    val artists: AlbumArtistListUiState? = null,
    val genres: GenreListUiState? = null,
    val playlists: PlaylistListUiState? = null,
    val folders: FolderListUiState? = null,
)

/**
 * Test robot for the Compose Library container ([LibraryScreen]) and the tab pages it hosts. Tests describe what
 * they see and tap; this robot owns the selectors.
 */
class LibraryScreenRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastTabSelected: LibraryTab? = null
        private set
    var lastTabsChanged: Pair<List<LibraryTab>, Set<LibraryTab>>? = null
        private set
    var lastSelectionAction: MediaActionType? = null
        private set
    var selectionCleared = false
        private set
    var lastSongClicked: Song? = null
        private set
    var lastSongLongClicked: Song? = null
        private set
    var lastAlbumClicked: Album? = null
        private set
    var lastArtistClicked: AlbumArtist? = null
        private set
    var lastGenreClicked: Genre? = null
        private set
    var lastPlaylistClicked: Playlist? = null
        private set
    var lastSmartPlaylistClicked: SmartPlaylist? = null
        private set
    var lastFolderClicked: Folder? = null
        private set
    var lastMore: Any? = null
        private set
    var shuffleClicked = false
        private set
    var newPlaylistClicked = false
        private set

    // -- Content setup --

    fun setContent(
        uiState: LibraryUiState,
        chrome: LibraryTabChrome = LibraryTabChrome(),
        pages: LibraryPageStates = LibraryPageStates(),
    ) {
        val capturingChrome = LibraryTabChrome(
            subtitle = chrome.subtitle,
            selection = chrome.selection,
            selectedCount = chrome.selectedCount,
            onClearSelection = {
                selectionCleared = true
                chrome.onClearSelection()
            },
            menu = chrome.menu,
        )
        rule.setContent {
            S2Theme {
                LibraryScreen(
                    uiState = uiState,
                    chrome = capturingChrome,
                    onTabSelected = { lastTabSelected = it },
                    onTabsChanged = { order, enabled -> lastTabsChanged = order to enabled },
                    onSelectionAction = { lastSelectionAction = it },
                ) { tab -> Page(tab, pages) }
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Page(tab: LibraryTab, pages: LibraryPageStates) {
        when (tab) {
            LibraryTab.Songs -> pages.songs?.let {
                SongsPage(it, onSongClick = { s -> lastSongClicked = s }, onSongLongClick = { s -> lastSongLongClicked = s }, onSongMore = { s -> lastMore = s }, onShuffle = { shuffleClicked = true })
            }

            LibraryTab.Albums -> pages.albums?.let {
                AlbumsPage(it, onAlbumClick = { a -> lastAlbumClicked = a }, onAlbumLongClick = {}, onAlbumMore = { a -> lastMore = a }, onShuffle = { shuffleClicked = true })
            }

            LibraryTab.Artists -> pages.artists?.let {
                ArtistsPage(it, onArtistClick = { a -> lastArtistClicked = a }, onArtistLongClick = {}, onArtistMore = { a -> lastMore = a })
            }

            LibraryTab.Genres -> pages.genres?.let {
                GenresPage(it, onGenreClick = { g -> lastGenreClicked = g }, onGenreMore = { g -> lastMore = g })
            }

            LibraryTab.Playlists -> pages.playlists?.let {
                PlaylistsPage(
                    it,
                    onPlaylistClick = { p -> lastPlaylistClicked = p },
                    onPlaylistMore = { p -> lastMore = p },
                    onSmartPlaylistClick = { p -> lastSmartPlaylistClicked = p },
                    onNewPlaylist = { newPlaylistClicked = true },
                )
            }

            LibraryTab.Folders -> pages.folders?.let {
                FoldersPage(it, onFolderClick = { f -> lastFolderClicked = f }, onFolderMore = { f -> lastMore = f }, onSongClick = { s -> lastSongClicked = s }, onSongMore = { s -> lastMore = s }, onNavigateUp = {})
            }
        } ?: Text("page:${tab.name}")
    }

    // -- Assertions --

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() || error("\"$text\" is shown")
    }

    fun assertTabSelected(label: String) {
        tab(label).assertIsSelected()
    }

    /** The tab labels in the tab row, in order. */
    fun tabLabels(): List<String> = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("library-tabs")))
        .fetchSemanticsNodes()
        .map { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().joinToString { it.text } }

    // -- Interactions --

    fun clickTab(label: String) {
        tab(label).performClick()
        rule.waitForIdle()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    fun scrollToText(listTag: String, text: String) {
        rule.onNodeWithTag(listTag).performScrollToNode(hasText(text))
    }

    fun openOverflow() {
        rule.onNodeWithTag("library-more").performClick()
        rule.waitForIdle()
    }

    fun clickSelectionAction(label: String) {
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitForIdle()
    }

    fun clearSelection() {
        rule.onNodeWithContentDescription("Clear selection").performClick()
        rule.waitForIdle()
    }

    fun toggleTabSwitch(tab: LibraryTab) {
        rule.onNodeWithTag("library-tab-switch-${tab.name}").performClick()
        rule.waitForIdle()
    }

    /** Taps "Move up" on the [index]th row of the edit-tabs sheet. */
    fun moveTabUp(index: Int) {
        rule.onAllNodesWithContentDescription("Move up")[index].performClick()
        rule.waitForIdle()
    }

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag("library-tabs")))
}
