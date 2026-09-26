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

        robot.tapText("harbour weather")
        robot.assertQuery("harbour weather")

        robot.tapRemoveRecentSearch(0)
        robot.removedRecentSearches shouldBe listOf("nightjar")
    }

    @Test
    fun `results are grouped by type`() {
        robot.setContent(SearchScenarios.results)

        robot.scrollTo("Nightjar & the Loom")
        robot.scrollTo("Night Bus Frequencies")
        robot.scrollTo("Night Ferry Lights")
        robot.scrollTo("Late Night")
    }

    @Test
    fun `the best match leads as the top result, bold where it matched`() {
        robot.setContent(SearchScenarios.results)

        robot.assertTextDisplayed("Top result")
        robot.assertBold("Nightjar & the Loom", "Night")
    }

    @Test
    fun `a long section shows the first few until see all`() {
        robot.setContent(SearchScenarios.manySongs)

        val songs = SearchScenarios.juniperSongs.map { it.name.orEmpty() }
        robot.scrollTo(songs[4])
        robot.assertTextNotShown(songs[5])
        robot.tapText("See all")

        robot.scrollTo(songs[7])
    }

    @Test
    fun `tapping a song plays from it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Route 29, Outbound")

        robot.playedSongs shouldBe listOf(1)
    }

    @Test
    fun `tapping an album, artist, genre or playlist opens it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Weather Systems")
        robot.tapText("Late Night")

        robot.openedAlbums shouldBe listOf(SearchScenarios.weatherSystems)
        robot.openedPlaylists shouldBe listOf(SearchScenarios.playlist)
    }

    @Test
    fun `tapping a genre opens it`() {
        robot.setContent(SearchScenarios.genreResults)

        robot.tapText("Jazz")

        robot.openedGenres shouldBe listOf(SearchScenarios.genre)
    }

    @Test
    fun `more options opens the actions for that item`() {
        robot.setContent(SearchScenarios.results)

        robot.tapMoreOn(0)

        robot.shownActions.single().selection shouldBe MediaSelection.AlbumArtists(SearchScenarios.nightjar)
    }

    @Test
    fun `filter chips reflect and toggle the categories`() {
        robot.setContent(SearchScenarios.songsOnly)

        robot.assertChipSelected("All", selected = false)
        robot.assertChipSelected("Songs", selected = true)
        robot.assertChipSelected("Albums", selected = false)
        robot.tapChip("Albums")
        robot.tapChip("All")

        robot.toggledCategories shouldBe listOf(SearchCategory.Albums)
        robot.allSelected shouldBe 1
    }

    @Test
    fun `search starts with only the All chip selected`() {
        robot.setContent(SearchUiState())

        robot.assertChipSelected("All", selected = true)
        listOf("Artists", "Albums", "Songs", "Genres", "Playlists").forEach { robot.assertChipSelected(it, selected = false) }
    }

    @Test
    fun `a query that finds nothing says so`() {
        robot.setContent(SearchScenarios.noResults)

        robot.assertTextDisplayed("No results for “zzzz”")
    }
}
