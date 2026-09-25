package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import io.kotest.matchers.shouldBe

/** Test robot for [SearchScreen]: records every callback so tests assert on what the screen asked for. */
class SearchRobot(private val rule: ComposeContentTestRule) {
    val queryState = TextFieldState()
    var searches = 0
        private set
    val toggledCategories = mutableListOf<SearchCategory>()
    val removedRecentSearches = mutableListOf<String>()
    val playedSongs = mutableListOf<Int>()
    val openedAlbums = mutableListOf<Album>()
    val openedArtists = mutableListOf<AlbumArtist>()
    val openedGenres = mutableListOf<Genre>()
    val openedPlaylists = mutableListOf<Playlist>()
    val shownActions = mutableListOf<MediaActionsTarget>()

    fun setContent(uiState: SearchUiState, theme: ThemeMode = ThemeMode.Light) {
        rule.setContent {
            S2AppTheme(AppThemeState(theme = theme)) {
                SearchScreen(uiState = uiState, queryState = queryState, callbacks = callbacks())
            }
        }
        rule.waitForIdle()
    }

    fun callbacks() = SearchCallbacks(
        onSearch = { searches++ },
        onToggleCategory = { toggledCategories += it },
        onRemoveRecentSearch = { removedRecentSearches += it },
        onSongClick = { playedSongs += it },
        onAlbumClick = { openedAlbums += it },
        onArtistClick = { openedArtists += it },
        onGenreClick = { openedGenres += it },
        onPlaylistClick = { openedPlaylists += it },
        onShowActions = { shownActions += it },
    )

    fun tapText(text: String) {
        scrollTo(text)
        rule.onNodeWithText(text).performClick()
    }

    fun scrollTo(text: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    fun tapMoreOn(index: Int) {
        rule.onAllNodesWithContentDescription("More options")[index].performClick()
    }

    fun tapRemoveRecentSearch(index: Int) {
        rule.onAllNodesWithContentDescription("Remove from recent searches")[index].performClick()
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotShown(text: String) {
        rule.onNode(hasText(text)).assertDoesNotExist()
    }

    /** Chips come first in the tree, above the result headers that share their labels. */
    fun assertChipSelected(label: String, selected: Boolean) {
        val chip = rule.onAllNodesWithText(label)[0]
        if (selected) chip.assertIsSelected() else chip.assertIsNotSelected()
    }

    fun tapChip(label: String) {
        rule.onAllNodesWithText(label)[0].performClick()
    }

    fun assertQuery(query: String) {
        rule.waitForIdle()
        queryState.text.toString() shouldBe query
    }
}
