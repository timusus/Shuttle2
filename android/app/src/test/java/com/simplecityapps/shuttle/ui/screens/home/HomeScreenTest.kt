package com.simplecityapps.shuttle.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = HomeRobot(composeTestRule)

    @Test
    fun `an empty library shows the empty state`() {
        robot.setContent(HomeScenarios.empty)

        robot.assertTextDisplayed("No music yet")
        robot.assertTextNotShown("Shuffle all")
    }

    @Test
    fun `a library shows its shelves`() {
        robot.setContent(HomeScenarios.content)

        robot.assertTextDisplayed("Recently played")
        robot.scrollTo("Recently added")
        robot.scrollTo("Most played")
        robot.scrollTo("Something different")
        robot.scrollTo("Saltmarsh Choir")
    }

    @Test
    fun `empty shelves are hidden`() {
        robot.setContent(HomeScenarios.unplayed)

        robot.assertTextNotShown("Recently played")
        robot.assertTextNotShown("Most played")
        robot.assertTextDisplayed("Recently added")
    }

    @Test
    fun `most played albums carry their play count`() {
        robot.setContent(HomeScenarios.content)

        robot.scrollTo("Soft Focus")
        robot.assertDescriptionDisplayed("14 plays")
    }

    @Test
    fun `tapping an album or artist opens it`() {
        robot.setContent(HomeScenarios.content)

        robot.tapText("Phase Garden")
        robot.tapText("Saltmarsh Choir")

        robot.openedAlbums shouldContainExactly listOf(HomeScenarios.phaseGarden)
        robot.openedArtists shouldContainExactly listOf(HomeScenarios.saltmarshChoir)
    }

    @Test
    fun `long-pressing a tile opens its actions`() {
        robot.setContent(HomeScenarios.content)

        robot.longPressText("Soft Focus")

        robot.shownActions.single().selection shouldBe MediaSelection.Albums(HomeScenarios.softFocus)
    }

    @Test
    fun `shuffle all and search are wired`() {
        robot.setContent(HomeScenarios.content)

        robot.tapText("Shuffle all")
        robot.tapDescription("Search")

        robot.shuffles shouldBe 1
        robot.searches shouldBe 1
    }

    @Test
    fun `the whats new card opens and dismisses`() {
        robot.setContent(HomeScenarios.whatsNew)

        robot.tapText("See what's new")
        robot.tapDescription("Dismiss")

        robot.whatsNewOpened shouldBe 1
        robot.whatsNewDismissed shouldBe 1
    }

    @Test
    fun `the whats new card is hidden once seen`() {
        robot.setContent(HomeScenarios.content)

        robot.assertTextNotShown("What's new in S2")
    }
}
