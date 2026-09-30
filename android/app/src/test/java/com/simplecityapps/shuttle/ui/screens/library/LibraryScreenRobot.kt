package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
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
    var playClicked = false
        private set
    var shuffleClicked = false
        private set
    var settingsOpened = false
        private set
    var newPlaylistClicked = false
        private set
    var lastViewModeChange: ViewMode? = null
        private set

    private var backDispatcher: OnBackPressedDispatcher? = null

    // -- Content setup --

    fun setContent(
        uiState: LibraryUiState,
        chrome: LibraryTabChrome = LibraryTabChrome(),
        pages: LibraryPageStates = LibraryPageStates(),
    ) {
        rule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            S2Theme { Screen(uiState, chrome, pages) }
        }
        rule.waitForIdle()
    }

    /**
     * The Albums tab with its view toggle wired to the page, as the destination wires both to the tab ViewModel: the
     * toggle switches the page between [ViewMode.Grid] and [ViewMode.List].
     */
    fun setAlbumsWithViewToggle(albums: AlbumListUiState) {
        rule.setContent {
            var viewMode by remember { mutableStateOf(albums.viewMode) }
            S2Theme {
                Screen(
                    libraryState(currentTab = LibraryTab.Albums),
                    LibraryTabChrome(viewMode = viewMode, onViewModeChange = { viewMode = it }),
                    LibraryPageStates(albums = albums.copy(viewMode = viewMode)),
                )
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Screen(uiState: LibraryUiState, chrome: LibraryTabChrome, pages: LibraryPageStates) {
        val capturingChrome = LibraryTabChrome(
            count = chrome.count,
            selection = chrome.selection,
            selectedCount = chrome.selectedCount,
            onClearSelection = {
                selectionCleared = true
                chrome.onClearSelection()
            },
            sortOptions = chrome.sortOptions,
            viewMode = chrome.viewMode,
            onViewModeChange = {
                lastViewModeChange = it
                chrome.onViewModeChange(it)
            },
            onPlay = chrome.onPlay?.let { play ->
                {
                    playClicked = true
                    play()
                }
            },
            onShuffle = chrome.onShuffle?.let { shuffle ->
                {
                    shuffleClicked = true
                    shuffle()
                }
            },
        )
        LibraryScreen(
            uiState = uiState,
            chrome = capturingChrome,
            onTabSelected = { lastTabSelected = it },
            onTabsChanged = { order, enabled -> lastTabsChanged = order to enabled },
            onSelectionAction = { lastSelectionAction = it },
            onOpenSettings = { settingsOpened = true },
        ) { tab -> Page(tab, pages) }
    }

    @Composable
    private fun Page(tab: LibraryTab, pages: LibraryPageStates) {
        when (tab) {
            LibraryTab.Songs -> pages.songs?.let {
                SongsPage(it, onSongClick = { s -> lastSongClicked = s }, onSongLongClick = { s -> lastSongLongClicked = s }, onSongMore = { s -> lastMore = s })
            }

            LibraryTab.Albums -> pages.albums?.let {
                AlbumsPage(it, onAlbumClick = { a -> lastAlbumClicked = a }, onAlbumLongClick = {}, onAlbumMore = { a -> lastMore = a })
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

    /** How many times [text] appears on screen, such as a count shown in both the top bar and the list. */
    fun countOfText(text: String): Int = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

    fun assertTextNotDisplayed(text: String) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() || error("\"$text\" is shown")
    }

    /** The page's fast scroller spans the page, so its track sits at the end edge rather than over the rows' start. */
    fun assertFastScrollerAtEndEdge() {
        val scroller = rule.onNodeWithTag("library-fast-scroller").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        check(scroller.left == root.left && scroller.right == root.right) { "fast scroller spans $scroller, page spans $root" }
    }

    /** Whether the screen takes a back press, rather than leaving it to the back stack. */
    fun backIsHandled(): Boolean = checkNotNull(backDispatcher).hasEnabledCallbacks()

    fun assertTabSelected(label: String) {
        tab(label).assertIsSelected()
    }

    fun assertContentDescriptionDisplayed(description: String) {
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    fun assertContentDescriptionNotDisplayed(description: String) {
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty() || error("\"$description\" is shown")
    }

    /** Whether [first] and [second] sit side by side, as tiles of a grid do, rather than one above the other as rows. */
    fun sideBySide(first: String, second: String): Boolean {
        val a = rule.onNodeWithText(first).fetchSemanticsNode().boundsInRoot
        val b = rule.onNodeWithText(second).fetchSemanticsNode().boundsInRoot
        return a.top == b.top && a.left != b.left
    }

    /** The section chip labels, in order. */
    fun tabLabels(): List<String> = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("library-sections")))
        .fetchSemanticsNodes()
        .map { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().joinToString { it.text } }

    // -- Interactions --

    fun clickTab(label: String) {
        tab(label).performClick()
        rule.waitForIdle()
    }

    /** Swipes the pager one page towards the end, as a finger would. */
    fun swipeToNextPage() {
        rule.onNodeWithTag("library-pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
    }

    /** Taps an icon button in the controls row (or anywhere) by its content description, such as "Play". */
    fun clickContentDescription(description: String) {
        rule.onNodeWithContentDescription(description).performClick()
        rule.waitForIdle()
    }

    fun openSort() {
        rule.onNodeWithTag("library-sort").performClick()
        rule.waitForIdle()
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    fun scrollToText(listTag: String, text: String) {
        rule.onNodeWithTag(listTag).performScrollToNode(hasText(text))
    }

    fun pressBack() {
        rule.runOnUiThread { checkNotNull(backDispatcher).onBackPressed() }
        rule.waitForIdle()
    }

    /** Drags the page's fast scroller thumb from the top of its track to the bottom, in small steps like a finger. */
    fun dragFastScrollerToBottom() {
        rule.onNodeWithTag("library-fast-scroller").performTouchInput {
            val thumb = topRight + Offset(-24.dp.toPx(), 24.dp.toPx())
            down(thumb)
            val steps = 40
            repeat(steps) { moveBy(Offset(0f, (bottom - thumb.y) / steps)) }
            up()
        }
        rule.waitForIdle()
    }

    fun openSettings() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
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

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag("library-sections")))
}
