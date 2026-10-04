package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * Test robot for [LibraryContent] around a plain list of [items] rows (each one's text is its row), the list
 * remembering its own scroll position the way the library pages do. Tests describe what they see; this robot owns
 * the selectors.
 */
class LibraryContentRobot(private val rule: ComposeContentTestRule) {

    private var state by mutableStateOf(LibraryContentState.Ready)
    private var progress by mutableStateOf<Progress?>(null)

    fun setContent(state: LibraryContentState, items: List<String>, scanProgress: Progress? = null) {
        this.state = state
        this.progress = scanProgress
        rule.setContent {
            S2Theme {
                LibraryContent(this.state, EMPTY_TITLE, scanProgress = progress, hasItems = items.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    LazyColumn(state = listState, modifier = Modifier.testTag(LIST_TAG)) {
                        items(items) { Text(it) }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** Moves the shown content to [state] without recreating the screen, as a scan starting or finishing does. */
    fun changeState(state: LibraryContentState, scanProgress: Progress? = null) {
        this.state = state
        this.progress = scanProgress
        rule.waitForIdle()
    }

    fun scrollToIndex(index: Int) {
        rule.onNodeWithTag(LIST_TAG).performScrollToIndex(index)
        rule.waitForIdle()
    }

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertListDisplayed() {
        rule.onNodeWithTag(LIST_TAG).assertIsDisplayed()
    }

    fun assertListNotDisplayed() {
        rule.onNodeWithTag(LIST_TAG).assertDoesNotExist()
    }

    /** The thin scan bar over the list, announced by description only (the full-screen placeholder shows it as text). */
    fun assertScanBarDisplayed() {
        rule.onNodeWithContentDescription(SCANNING).assertIsDisplayed()
        rule.onNodeWithText(SCANNING).assertDoesNotExist()
    }

    fun assertScanBarNotDisplayed() {
        rule.onNodeWithContentDescription(SCANNING).assertDoesNotExist()
    }

    /** The full-screen scan placeholder in place of the list. */
    fun assertScanPlaceholderDisplayed() {
        rule.onNodeWithText(SCANNING).assertIsDisplayed()
    }

    companion object {
        const val EMPTY_TITLE = "Nothing here"
        private const val SCANNING = "Scanning your library"
        private const val LIST_TAG = "library-content-list"
    }
}
