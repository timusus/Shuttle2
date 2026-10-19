package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Render smoke test for [ExcludedSongsScreen]; the decisions are covered by `ExcludedSongsViewStateTest`. */
@RunWith(RobolectricTestRunner::class)
class ExcludedSongsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = ExcludedSongsRobot(composeTestRule)

    private val songs = listOf(
        createSong(id = 1, name = "Alpha").copy(blacklisted = true),
        createSong(id = 2, name = "Beta").copy(blacklisted = true)
    )

    @Test
    fun `lists the excluded songs and a song's menu includes it`() {
        robot.setContent(ExcludedSongsUiState(songs = songs, loading = false))

        robot.assertDisplayed("Alpha")
        robot.assertDisplayed("Beta")

        robot.openMenu(1)
        robot.tapText("Include in library")

        robot.included.map { it.id } shouldBe listOf(2L)
    }
}
