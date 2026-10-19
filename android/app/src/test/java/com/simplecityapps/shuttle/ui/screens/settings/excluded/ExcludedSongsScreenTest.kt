package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Render tests for [ExcludedSongsScreen]: the resolved copy and each control reaching its event.
 * The decisions are covered by `ExcludedSongsViewStateTest`, and the menu and event routing by `ExcludedSongsEventsTest`.
 */
@RunWith(RobolectricTestRunner::class)
class ExcludedSongsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = ExcludedSongsRobot(composeTestRule)

    private val songs = listOf(
        createSong(id = 1, name = "Alpha").copy(blacklisted = true),
        createSong(id = 2, name = "Beta").copy(blacklisted = true)
    )

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the empty copy is a string resource only the rendered tree resolves.
    @Test
    fun `shows the empty state with nothing excluded`() {
        robot.setContent(ExcludedSongsUiState(songs = emptyList(), loading = false))

        robot.assertDisplayed("Exclude list is empty")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks each row reaches the rendered list.
    @Test
    fun `lists the excluded songs`() {
        robot.setContent(ExcludedSongsUiState(songs = songs, loading = false))

        robot.assertDisplayed("Alpha")
        robot.assertDisplayed("Beta")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the menu is a popup only the rendered tree shows, and its click must reach onEvent.
    @Test
    fun `a song's menu includes it`() {
        robot.setContent(ExcludedSongsUiState(songs = songs, loading = false))

        robot.openMenu(1)
        robot.tapText("Include in library")

        robot.included.map { it.id } shouldBe listOf(2L)
        robot.assertNotShown("Include in library")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the confirm dialog is a window only the rendered tree shows, and its confirm must reach onEvent.
    @Test
    fun `include all asks first`() {
        robot.setContent(ExcludedSongsUiState(songs = songs, loading = false))

        robot.tapIncludeAll()
        robot.includeAllCount shouldBe 0
        robot.assertDisplayed("Every excluded song returns to your library.")
        robot.tapText("Include all")

        robot.includeAllCount shouldBe 1
    }
}
