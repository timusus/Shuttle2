package com.simplecityapps.shuttle.ui.common.mediaactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The shared actions sheet and playlist picker every destination, and the player, hosts. */
@RunWith(RobolectricTestRunner::class)
class MediaActionsHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val handled = mutableListOf<MediaAction>()
    private val song = createSong(name = "Phase Garden")
    private val roadTrip = createPlaylist(name = "Road trip")
    private var state: MediaActionsState? = null

    private fun setContent(target: MediaActionsTarget) {
        val handler = mockk<MediaActionHandler>()
        coEvery { handler.handle(any()) } answers {
            val action = firstArg<MediaAction>()
            handled += action
            if (action is MediaAction.Delete && !action.confirmed) {
                MediaActionResult.ConfirmationRequired(MediaActionMessage.ConfirmDelete(song.name, 1), action.copy(confirmed = true))
            } else {
                MediaActionResult.None
            }
        }
        val available = mockk<AvailableMediaActions>()
        every { available(any()) } returns flowOf(MediaActionType.entries.toList())
        val observePlaylists = mockk<ObservePlaylists>()
        every { observePlaylists(any()) } returns flowOf(listOf(roadTrip))
        val viewModel = MediaActionsViewModel(handler, available, observePlaylists)
        rule.setContent {
            S2Theme {
                MediaActionsHost(onNavigate = {}, viewModel = viewModel, systemDeletes = false) { actions ->
                    state = actions
                }
            }
        }
        rule.runOnUiThread { state!!.showActions(target) }
        rule.waitForIdle()
    }

    private fun target(
        onlyTypes: Set<MediaActionType>? = null,
        leadingActions: List<S2Action> = emptyList(),
    ) = MediaActionsTarget(
        title = "Phase Garden",
        subtitle = "Juniper Static",
        selection = MediaSelection.Songs(song),
        placeholder = ArtworkPlaceholder.Song,
        leadingActions = leadingActions,
        onlyTypes = onlyTypes,
    )

    @Test
    fun `the sheet lists every available action`() {
        setContent(target())
        rule.onNodeWithText("Shuffle").assertIsDisplayed()
        rule.onNodeWithText("Go to album").assertIsDisplayed()
    }

    @Test
    fun `onlyTypes drops the actions a screen does not offer`() {
        setContent(target(onlyTypes = setOf(MediaActionType.GoToAlbum)))
        rule.onNodeWithText("Go to album").assertIsDisplayed()
        rule.onNodeWithText("Shuffle").assertDoesNotExist()
    }

    @Test
    fun `a leading action runs the screen's own callback`() {
        var removed = 0
        setContent(target(onlyTypes = emptySet(), leadingActions = listOf(S2Action("Remove from Queue", { removed++ }, Icons.Rounded.Close))))
        rule.onNodeWithText("Remove from Queue").performClick()
        removed shouldBe 1
    }

    @Test
    fun `Add to Playlist opens the picker with New Playlist first, then the playlists`() {
        setContent(target(onlyTypes = setOf(MediaActionType.AddToPlaylist)))
        rule.onNodeWithText("Add to Playlist").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("New Playlist").assertIsDisplayed()
        rule.onNodeWithText("Road trip").performClick()
        rule.waitForIdle()

        handled shouldBe listOf(MediaAction.AddToPlaylist(MediaSelection.Songs(song), roadTrip))
    }

    @Test
    fun `confirming a Delete sends it again, confirmed`() {
        setContent(target(onlyTypes = setOf(MediaActionType.Delete)))
        rule.onNodeWithText("Delete").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("'Phase Garden' will be permanently deleted").assertIsDisplayed()

        rule.onAllNodesWithText("Delete").onLast().performClick()
        rule.waitForIdle()

        val selection = MediaSelection.Songs(song)
        handled shouldBe listOf(MediaAction.Delete(selection), MediaAction.Delete(selection, confirmed = true))
        rule.onNodeWithText("'Phase Garden' will be permanently deleted").assertDoesNotExist()
    }

    @Test
    fun `cancelling a Delete confirmation sends nothing more`() {
        setContent(target(onlyTypes = setOf(MediaActionType.Delete)))
        rule.onNodeWithText("Delete").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        handled shouldBe listOf(MediaAction.Delete(MediaSelection.Songs(song)))
        rule.onNodeWithText("'Phase Garden' will be permanently deleted").assertDoesNotExist()
    }
}
