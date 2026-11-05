package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.sorting.SongListSort
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenreDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the genre, its album header and its songs`() {
        robot.setGenre(readyGenreDetail())

        robot.assertTextDisplayed("Electronic")
        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("Phase Garden")
        robot.scrollTo("Chlorophyll Loop")
        robot.assertTextDisplayed("Chlorophyll Loop")
    }

    @Test
    fun `the hero subtitle gives the song count and duration`() {
        robot.setGenre(readyGenreDetail())

        robot.assertHeroTextDisplayed("3\u00a0songs · 12:00")
    }

    @Test
    fun `an album header's thumbnail opens the album`() {
        robot.setGenre(readyGenreDetail())

        robot.clickOpenAlbum("Phase Garden")

        robot.lastAlbumClicked?.name shouldBe "Phase Garden"
    }

    @Test
    fun `an album header folds and a song plays from its position`() {
        val state = readyGenreDetail()
        robot.setGenre(state)

        robot.clickText("Petal Arithmetic")
        robot.lastPlayed shouldBe (state.songs to 2)

        robot.clickText("Phase Garden")
        robot.lastAlbumToggled?.name shouldBe "Phase Garden"
    }

    @Test
    fun `flat the songs header lists the songs and its menu picks a sort`() {
        robot.setGenre(readyGenreDetail(sort = SongListSort.SongTitle))

        robot.assertTextDisplayed("Songs")
        robot.openPlaylistSortMenu()
        robot.clickMenuItem("Album title")

        robot.lastPlaylistSort shouldBe SongListSort.AlbumTitle
    }

    @Test
    fun `grouped Collapse all folds every album`() {
        robot.setGenre(readyGenreDetail())

        robot.clickContentDescription("Collapse all")

        robot.collapsedAll shouldBe true
    }

    @Test
    fun `Shuffle shuffles and the overflow opens the genre's actions`() {
        val state = readyGenreDetail()
        robot.setGenre(state)

        robot.clickShuffle()
        robot.shuffleClicked shouldBe true

        robot.clickOverflow()
        robot.lastMore shouldBe state.genre
    }

    @Test
    fun `a genre that is gone says it is not in the library`() {
        robot.setGenre(missingGenreDetail)

        robot.assertTextDisplayed("Not in your library")
    }
}
