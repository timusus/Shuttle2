package com.simplecityapps.shuttle.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import io.kotest.matchers.shouldBe

/** Test robot for [HomeScreen]: records every callback so tests assert on what the screen asked for. */
class HomeRobot(private val rule: ComposeContentTestRule) {
    var searches = 0
        private set
    var shuffles = 0
        private set
    var whatsNewOpened = 0
        private set
    var whatsNewDismissed = 0
        private set
    val openedAlbums = mutableListOf<Album>()
    val openedArtists = mutableListOf<AlbumArtist>()
    val shownActions = mutableListOf<MediaActionsTarget>()

    fun setContent(
        uiState: HomeUiState,
        theme: ThemeMode = ThemeMode.Light,
        emptyContent: (@Composable (Modifier) -> Unit)? = null,
    ) {
        rule.setContent {
            S2AppTheme(AppThemeState(theme = theme)) {
                HomeScreen(uiState = uiState, callbacks = callbacks(), emptyContent = emptyContent)
            }
        }
        rule.waitForIdle()
    }

    private fun callbacks() = HomeCallbacks(
        onSearch = { searches++ },
        onShuffleAll = { shuffles++ },
        onOpenWhatsNew = { whatsNewOpened++ },
        onDismissWhatsNew = { whatsNewDismissed++ },
        onAlbumClick = { openedAlbums += it },
        onArtistClick = { openedArtists += it },
        onShowActions = { shownActions += it },
    )

    fun scrollTo(text: String) {
        // The first scrollable node is Home's own list; the shelves nest inside it.
        rule.onAllNodes(hasScrollToIndexAction())[0].performScrollToNode(hasText(text))
    }

    fun tapText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performClick()
    }

    /** Taps text outside Home's own scrolling list, such as content in the empty-state slot (#422). */
    fun tapVisibleText(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
    }

    fun longPressText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performTouchInput { longClick() }
    }

    fun tapDescription(description: String) {
        rule.onNodeWithContentDescription(description).performClick()
    }

    fun assertTextDisplayed(text: String) {
        rule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }

    fun assertTextNotShown(text: String) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().size shouldBe 0
    }

    fun assertDescriptionDisplayed(description: String) {
        rule.onNodeWithContentDescription(description).assertIsDisplayed()
    }
}
