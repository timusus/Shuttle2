package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
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
    var trialOpened = false
        private set
    var newPlaylistClicked = false
        private set
    var lastViewModeChange: ViewMode? = null
        private set

    private var backDispatcher: OnBackPressedDispatcher? = null
    private var layoutDirection = LayoutDirection.Ltr

    // -- Content setup --

    /** The container on [uiState]; every page leads with [controls], and [chrome] carries the current tab's selection. */
    fun setContent(
        uiState: LibraryUiState,
        controls: LibraryTabControls = LibraryTabControls(),
        pages: LibraryPageStates = LibraryPageStates(),
        chrome: LibraryTabChrome = LibraryTabChrome(),
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        trialDaysLeft: Int? = null,
    ) {
        this.layoutDirection = layoutDirection
        rule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, LocalDensity provides Density(density.density, fontScale)) {
                S2Theme { Screen(uiState, chrome, controls, pages, trialDaysLeft) }
            }
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
                    LibraryTabChrome(),
                    LibraryTabControls(viewMode = viewMode, onViewModeChange = { viewMode = it }),
                    LibraryPageStates(albums = albums.copy(viewMode = viewMode)),
                    trialDaysLeft = null,
                )
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun Screen(uiState: LibraryUiState, chrome: LibraryTabChrome, controls: LibraryTabControls, pages: LibraryPageStates, trialDaysLeft: Int?) {
        val capturingChrome = LibraryTabChrome(
            selection = chrome.selection,
            selectedCount = chrome.selectedCount,
            onClearSelection = {
                selectionCleared = true
                chrome.onClearSelection()
            },
        )
        val capturingControls = LibraryTabControls(
            count = controls.count,
            sortOptions = controls.sortOptions,
            viewMode = controls.viewMode,
            onViewModeChange = {
                lastViewModeChange = it
                controls.onViewModeChange(it)
            },
            onPlay = controls.onPlay?.let { play ->
                {
                    playClicked = true
                    play()
                }
            },
            onShuffle = controls.onShuffle?.let { shuffle ->
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
            trialDaysLeft = trialDaysLeft,
            onOpenTrial = { trialOpened = true },
        ) { tab -> Page(tab, pages, capturingControls) }
    }

    @Composable
    private fun Page(tab: LibraryTab, pages: LibraryPageStates, controls: LibraryTabControls) {
        when (tab) {
            LibraryTab.Songs -> pages.songs?.let {
                SongsPage(it, onSongClick = { s -> lastSongClicked = s }, onSongLongClick = { s -> lastSongLongClicked = s }, onSongMore = { s -> lastMore = s }, controls = controls)
            }

            LibraryTab.Albums -> pages.albums?.let {
                AlbumsPage(it, onAlbumClick = { a -> lastAlbumClicked = a }, onAlbumLongClick = {}, onAlbumMore = { a -> lastMore = a }, controls = controls)
            }

            LibraryTab.Artists -> pages.artists?.let {
                ArtistsPage(it, onArtistClick = { a -> lastArtistClicked = a }, onArtistLongClick = {}, onArtistMore = { a -> lastMore = a }, controls = controls)
            }

            LibraryTab.Genres -> pages.genres?.let {
                GenresPage(it, onGenreClick = { g -> lastGenreClicked = g }, onGenreMore = { g -> lastMore = g }, controls = controls)
            }

            LibraryTab.Playlists -> pages.playlists?.let {
                PlaylistsPage(
                    it,
                    onPlaylistClick = { p -> lastPlaylistClicked = p },
                    onPlaylistMore = { p -> lastMore = p },
                    onSmartPlaylistClick = { p -> lastSmartPlaylistClicked = p },
                    onNewPlaylist = { newPlaylistClicked = true },
                    controls = controls,
                )
            }

            LibraryTab.Folders -> pages.folders?.let {
                FoldersPage(it, onFolderClick = { f -> lastFolderClicked = f }, onFolderMore = { f -> lastMore = f }, onSongClick = { s -> lastSongClicked = s }, onSongMore = { s -> lastMore = s }, onNavigateUp = {})
            }
        } ?: Column {
            // A stand-in page still leads with the controls, as a real one does.
            if (!controls.isEmpty) LibraryControlsRow(controls)
            Text("page:${tab.name}")
        }
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

    /** The section chip labels, in order: the row is lazy, so this scrolls it end to end and reads each window. */
    fun tabLabels(): List<String> {
        val labels = mutableListOf<String>()
        var index = 0
        while (runCatching { sections().performScrollToIndex(index) }.isSuccess) {
            rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("library-sections")))
                .fetchSemanticsNodes()
                .sortedBy { node -> node.boundsInRoot.left }
                .map { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().joinToString { it.text } }
                .forEach { label -> if (label !in labels) labels += label }
            index++
        }
        return labels
    }

    /** The sort button, found by what a screen reader announces for it, such as "Sort: Album name". */
    fun assertSortAnnounced(description: String) {
        rule.onNodeWithTag("library-sort").assertContentDescriptionEquals(description)
    }

    /** The sort button's touch target, which may be larger than what it draws. */
    fun sortTouchTarget(): DpSize {
        val bounds = rule.onNodeWithTag("library-sort").fetchSemanticsNode().touchBoundsInRoot
        return with(rule.density) { DpSize(bounds.width.toDp(), bounds.height.toDp()) }
    }

    /** Whether the controls row's actions all lie within the screen: none pushed off an edge by wide text. */
    fun controlsRowFitsScreen(): Boolean {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag("library-controls").fetchSemanticsNode().boundsInRoot
        return row.left >= root.left && row.right <= root.right &&
            listOf("Shuffle", "Play").all { description ->
                val bounds = rule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
                bounds.left >= root.left && bounds.right <= root.right
            }
    }

    /** The controls row's size, which grows with the text. */
    fun controlsRowSize(): DpSize {
        val bounds = rule.onNodeWithTag("library-controls").fetchSemanticsNode().boundsInRoot
        return with(rule.density) { DpSize(bounds.width.toDp(), bounds.height.toDp()) }
    }

    /** Whether the controls row is composed in the current page. */
    fun controlsShown(): Boolean = rule.onAllNodesWithTag("library-controls").fetchSemanticsNodes().isNotEmpty()

    /** Scrolls the page's list, tagged [listTag], so its item at [index] leads, as a fling would. */
    fun scrollPageTo(listTag: String, index: Int) {
        rule.onNodeWithTag(listTag).performScrollToIndex(index)
        rule.waitForIdle()
    }

    // -- Interactions --

    /** Taps a section chip, first scrolling the chip row until it's on screen, as a finger would. */
    fun clickTab(label: String) {
        sections().performScrollToNode(hasText(label))
        tab(label).performClick()
        rule.waitForIdle()
    }

    /** Swipes the pager one page towards the end, as a finger would: leftwards, or rightwards in a right-to-left layout. */
    fun swipeToNextPage() {
        rule.onNodeWithTag("library-pager").performTouchInput { if (layoutDirection == LayoutDirection.Ltr) swipeLeft() else swipeRight() }
        rule.waitForIdle()
    }

    /** Taps an icon button in the controls row (or anywhere) by its content description, such as "Play". */
    fun clickContentDescription(description: String) {
        rule.onNodeWithContentDescription(description).performClick()
        rule.waitForIdle()
    }

    /** The title shares its row with the settings action, rather than sitting under it. */
    fun titleSharesRowWithSettings(): Boolean {
        val title = rule.onNodeWithText("Library").fetchSemanticsNode().boundsInRoot
        val settings = rule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
        return title.top < settings.bottom && settings.top < title.bottom
    }

    /** The section chip is fully inside the chip row, neither clipped at its start nor at its end. */
    fun sectionChipIsFullyVisible(label: String): Boolean {
        val row = rule.onNodeWithTag("library-sections").fetchSemanticsNode().boundsInRoot
        val chip = tab(label).fetchSemanticsNode().boundsInRoot
        return chip.left >= row.left && chip.right <= row.right
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

    fun openTrial() {
        rule.onNodeWithTag("library-trial-chip").performClick()
        rule.waitForIdle()
    }

    fun trialChipShown(): Boolean = rule.onAllNodesWithTag("library-trial-chip").fetchSemanticsNodes().isNotEmpty()

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

    private fun sections() = rule.onNodeWithTag("library-sections")
}
