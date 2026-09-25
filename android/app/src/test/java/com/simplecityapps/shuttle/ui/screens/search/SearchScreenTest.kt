package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SearchRobot(composeTestRule)

    @Test
    fun `with nothing searched yet it invites a search`() {
        robot.setContent(SearchScenarios.start)

        robot.assertTextDisplayed("Find artists, albums, songs, genres and playlists")
    }

    @Test
    fun `a recent search fills the field and can be removed`() {
        robot.setContent(SearchScenarios.recent)
        robot.assertTextDisplayed("Recent searches")

        robot.tapText("massive attack")
        robot.assertQuery("massive attack")

        robot.tapRemoveRecentSearch(0)
        robot.removedRecentSearches shouldBe listOf("radiohead")
    }

    @Test
    fun `results are grouped by type`() {
        robot.setContent(SearchScenarios.results)

        robot.scrollTo("Radiohead")
        robot.scrollTo("OK Computer")
        robot.scrollTo("Paranoid Android")
        robot.scrollTo("Alternative")
        robot.scrollTo("Radio favourites")
    }

    @Test
    fun `the best match leads as the top result, bold where it matched`() {
        robot.setContent(SearchScenarios.results)

        robot.assertTextDisplayed("Top result")
        robot.assertBold("Radiohead", "Radiohead")
    }

    @Test
    fun `a long section shows the first few until see all`() {
        robot.setContent(SearchScenarios.manySongs)

        robot.scrollTo("Track 5")
        robot.assertTextNotShown("Track 6")
        robot.tapText("See all")

        robot.scrollTo("Track 8")
    }

    @Test
    fun `tapping a song plays from it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Paranoid Android")

        robot.playedSongs shouldBe listOf(1)
    }

    @Test
    fun `tapping an album, artist, genre or playlist opens it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Kid A")
        robot.tapText("Alternative")
        robot.tapText("Radio favourites")

        robot.openedAlbums shouldBe listOf(SearchScenarios.kidA)
        robot.openedGenres shouldBe listOf(SearchScenarios.genre)
        robot.openedPlaylists shouldBe listOf(SearchScenarios.playlist)
    }

    @Test
    fun `more options opens the actions for that item`() {
        robot.setContent(SearchScenarios.results)

        robot.tapMoreOn(0)

        robot.shownActions.single().selection shouldBe MediaSelection.AlbumArtists(SearchScenarios.radiohead)
    }

    @Test
    fun `filter chips reflect and toggle the categories`() {
        robot.setContent(SearchScenarios.songsOnly)

        robot.assertChipSelected("Songs", selected = true)
        robot.assertChipSelected("Albums", selected = false)
        robot.tapChip("Albums")

        robot.toggledCategories shouldBe listOf(SearchCategory.Albums)
    }

    @Test
    fun `a query that finds nothing says so`() {
        robot.setContent(SearchScenarios.noResults)

        robot.assertTextDisplayed("No results for “zzzz”")
    }
}
