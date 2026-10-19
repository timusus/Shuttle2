package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Render smoke test for [SearchScreen]; the decisions are covered by `SearchViewStateTest`. */
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SearchRobot(composeTestRule)

    @Test
    fun `results render grouped under the top result and tapping a song plays from it`() {
        robot.setContent(SearchScenarios.results)

        robot.assertTextDisplayed("Top result")
        robot.scrollTo("Nightjar & the Loom")
        robot.scrollTo("Night Bus Frequencies")
        robot.scrollTo("Night Ferry Lights")

        robot.tapText("Route 29, Outbound")

        robot.playedSongs shouldBe listOf(1)
    }

    // The search field is a design-system composable; there is no view-state field to map, only its absence to guard.
    @Test
    fun `the field has no Settings button`() {
        robot.setContent(SearchScenarios.start)

        robot.assertNoSettingsButton()
    }
}
