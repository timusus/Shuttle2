package com.simplecityapps.shuttle.ui.screens.search

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Render tests for [SearchScreen]'s wiring: copy, rendered state and each control reaching its event. The decisions
 * behind them are covered by `SearchViewStateTest`, `SearchResultItemsTest` and `SearchEventsTest`.
 */
@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SearchRobot(composeTestRule)

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the start copy is string resources only the rendered tree resolves.
    @Test
    fun `with nothing searched yet it invites a search`() {
        robot.setContent(SearchScenarios.start)

        robot.assertTextDisplayed("Find artists, albums, songs, genres and playlists")
    }

    // The search field is a design-system composable; there is no view-state field to map, only its absence to guard.
    @Test
    fun `the field has no Settings button`() {
        robot.setContent(SearchScenarios.start)

        robot.assertNoSettingsButton()
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the field's text and the row's remove button live only in the rendered tree.
    @Test
    fun `a recent search fills the field and can be removed`() {
        robot.setContent(SearchScenarios.recent)
        robot.assertTextDisplayed("Recent searches")

        robot.tapText("harbour weather")
        robot.assertQuery("harbour weather")

        robot.tapRemoveRecentSearch(0)
        robot.removedRecentSearches shouldBe listOf("nightjar")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks every group's rows reach the LazyColumn.
    @Test
    fun `results are grouped by type`() {
        robot.setContent(SearchScenarios.results)

        robot.scrollTo("Nightjar & the Loom")
        robot.scrollTo("Night Bus Frequencies")
        robot.scrollTo("Night Ferry Lights")
        robot.scrollTo("Late Night")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the header copy and match bolding are rendered text and styles.
    @Test
    fun `the best match leads as the top result, bold where it matched`() {
        robot.setContent(SearchScenarios.results)

        robot.assertTextDisplayed("Top result")
        robot.assertBold("Nightjar & the Loom", "Night")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; See all's state lives in the composable, so only the tree shows it expand.
    @Test
    fun `a long section shows the first few until see all`() {
        robot.setContent(SearchScenarios.manySongs)

        val songs = SearchScenarios.juniperSongs.map { it.name.orEmpty() }
        robot.scrollTo(songs[4])
        robot.assertTextNotShown(songs[5])
        robot.tapText("See all")

        robot.scrollTo(songs[7])
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the row's click reaches onEvent.
    @Test
    fun `tapping a song plays from it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Route 29, Outbound")

        robot.playedSongs shouldBe listOf(1)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the rows' clicks reach onEvent.
    @Test
    fun `tapping an album, artist, genre or playlist opens it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Weather Systems")
        robot.tapText("Late Night")

        robot.openedAlbums shouldBe listOf(SearchScenarios.weatherSystems)
        robot.openedPlaylists shouldBe listOf(SearchScenarios.playlist)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the count's plural is a resource only the rendered tree resolves.
    @Test
    fun `an artist found only by their track credits counts their songs and opens their page`() {
        val featured = createAlbumArtist(name = "Chris Martin", albumCount = 0, songCount = 2, appearsOnCount = 1)
        robot.setContent(
            SearchUiState(content = SearchContent.Results("chris", SearchResults(artists = listOf(SearchHit(featured, SearchQuery.parse("chris"))), top = SearchCategory.Artists))),
        )

        // The plural keeps the count and its unit together with a no-break space.
        robot.assertTextDisplayed("2 songs")
        robot.tapText("Chris Martin")

        robot.openedArtists shouldBe listOf(featured)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the genre row's click reaches onEvent.
    @Test
    fun `tapping a genre opens it`() {
        robot.setContent(SearchScenarios.genreResults)

        robot.tapText("Jazz")

        robot.openedGenres shouldBe listOf(SearchScenarios.genre)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks More options reaches onEvent with the row's target.
    @Test
    fun `more options opens the actions for that item`() {
        robot.setContent(SearchScenarios.results)

        robot.tapMoreOn(0)

        robot.shownActions.single().selection shouldBe MediaSelection.AlbumArtists(SearchScenarios.nightjar)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the top result's Play button reaches onEvent.
    @Test
    fun `the top result's play button plays it`() {
        robot.setContent(SearchScenarios.results)

        robot.tapText("Play")

        robot.playedSelections shouldBe listOf(MediaSelection.AlbumArtists(SearchScenarios.nightjar))
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the top song's Play button reaches onEvent.
    @Test
    fun `the top song's play button plays from its place among the songs`() {
        robot.setContent(SearchScenarios.songsOnly)

        robot.tapText("Play")

        robot.playedSongs shouldBe listOf(0)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; chip selection is rendered semantics and the taps must reach onEvent.
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

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; chip selection is rendered semantics.
    @Test
    fun `search starts with only the All chip selected`() {
        robot.setContent(SearchUiState())

        robot.assertChipSelected("All", selected = true)
        listOf("Artists", "Albums", "Songs", "Genres", "Playlists").forEach { robot.assertChipSelected(it, selected = false) }
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the copy is a string resource only the rendered tree resolves.
    @Test
    fun `a query that finds nothing says so`() {
        robot.setContent(SearchScenarios.noResults)

        robot.assertTextDisplayed("No results for “zzzz”")
    }
}
