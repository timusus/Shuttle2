package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the album, its artist, year and songs`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextDisplayed("Phase Garden")
        robot.assertTextDisplayed("Juniper Static\u00A0· 2021\u00A0· 3\u00A0songs", substring = true)
        robot.assertTextDisplayed("Chlorophyll Loop")
        robot.assertTextDisplayed("Soft Machines at Dawn")
    }

    @Test
    fun `the header gives the song count and a runtime under an hour in minutes`() {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 12, duration = 2_700_000, year = 2021)
        robot.setAlbum(readyAlbumDetail(album = album))

        robot.assertTextDisplayed("12 songs · 45 min", substring = true)
    }

    @Test
    fun `the runtime of an hour or more shows hours and minutes`() {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 1, duration = 7_500_000, year = 2021)
        robot.setAlbum(readyAlbumDetail(album = album))

        robot.assertTextDisplayed("1 song · 2 hr, 5 min", substring = true)
    }

    @Test
    fun `an album with no duration shows no runtime and no stray separator`() {
        assertNoRuntime(duration = 0)
    }

    @Test
    fun `an album under a minute shows no runtime and no stray separator`() {
        assertNoRuntime(duration = 59_000)
    }

    private fun assertNoRuntime(duration: Int) {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 2, duration = duration, year = 2021)
        robot.setAlbum(readyAlbumDetail(album = album))

        robot.assertTextDisplayed("Juniper Static · 2021 · 2 songs", substring = true)
        robot.assertTextNotDisplayed("2 songs ·", substring = true)
        robot.assertTextNotDisplayed("min", substring = true)
    }

    @Test
    fun `59 minutes stays in minutes`() {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 1, duration = 3_540_000, year = 2021)
        robot.setAlbum(readyAlbumDetail(album = album))

        robot.assertTextDisplayed("1 song · 59 min", substring = true)
    }

    @Test
    fun `exactly an hour shows no minutes`() {
        val album = createAlbum(name = "Phase Garden", albumArtist = "Juniper Static", songCount = 1, duration = 3_600_000, year = 2021)
        robot.setAlbum(readyAlbumDetail(album = album))

        robot.assertTextDisplayed("1 song · 1 hr", substring = true)
        robot.assertTextNotDisplayed("0 min", substring = true)
    }

    @Test
    fun `Play starts the album from the top and a song plays from its position`() {
        val songs = phaseGardenSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs))

        robot.clickPlay()
        robot.lastPlayed shouldBe (songs to 0)

        robot.clickText("Soft Machines at Dawn")
        robot.lastPlayed shouldBe (songs to 1)
    }

    @Test
    fun `Shuffle shuffles the album`() {
        robot.setAlbum(readyAlbumDetail())

        robot.clickShuffle()

        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `the overflow opens the album's actions and a row's opens the song's`() {
        val state = readyAlbumDetail()
        robot.setAlbum(state)

        robot.clickOverflow()
        robot.lastMore shouldBe state.album

        robot.clickRowMore(1)
        robot.lastMore shouldBe state.songs[1]
    }

    @Test
    fun `a two-disc album groups its songs under disc headers`() {
        robot.setAlbum(readyAlbumDetail(songs = phaseGardenSongs(disc = 1) + phaseGardenSongs(disc = 2)))

        robot.assertTextDisplayed("Disc 1")
        robot.scrollTo("Disc 2")
        robot.assertTextDisplayed("Disc 2")
    }

    @Test
    fun `a one-disc album has no disc header`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextNotDisplayed("Disc 1")
    }

    @Test
    fun `a classical album groups its movements under the work with the composer`() {
        robot.setAlbum(classicalAlbumDetail())

        robot.assertTextDisplayed("Sonata No. 3")
        robot.assertTextDisplayed("Harrow")
        robot.assertTextDisplayed("I. Allegro")
        robot.assertTextDisplayed("II. Adagio")
        robot.assertTextDisplayed("III. Presto")
        robot.assertTextNotDisplayed("Sonata No. 3: Allegro")
    }

    @Test
    fun `a classical album's subtitle names the conductor`() {
        robot.setAlbum(classicalAlbumDetail())

        robot.assertTextDisplayed("Ines Varga", substring = true)
    }

    @Test
    fun `a lone work gets no header and an untagged track keeps its title`() {
        robot.setAlbum(classicalAlbumDetail())

        robot.scrollTo("Encore")
        robot.assertTextDisplayed("Encore")
        robot.assertTextNotDisplayed("Prelude in D")
        robot.assertTextNotDisplayed("Harrow · arr. T. Okoye")
    }

    @Test
    fun `with Classical display off the album renders as before`() {
        robot.setAlbum(classicalAlbumDetail(classical = false))

        robot.assertTextDisplayed("Sonata No. 3: Allegro")
        robot.assertTextNotDisplayed("I. Allegro")
        robot.assertTextNotDisplayed("Ines Varga", substring = true)
    }

    @Test
    fun `the playing song is marked`() {
        val songs = phaseGardenSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs, currentSong = songs[0]))

        robot.assertNowPlayingShown()
    }

    @Test
    fun `More by lists the artist's other albums, a tap opens one and a long press opens its actions`() {
        val other = createAlbum(name = "Verdigris", albumArtist = "Juniper Static", year = 2019)
        robot.setAlbum(readyAlbumDetail(moreByArtist = listOf(other)))

        robot.scrollTo("More by Juniper Static")
        robot.assertTextDisplayed("More by Juniper Static")
        robot.clickText("Verdigris")
        robot.lastAlbumClicked shouldBe other

        robot.longClickText("Verdigris")
        robot.lastMore shouldBe other
    }

    @Test
    fun `More by still shows when no artist name resolved`() {
        val album = createAlbum(name = "Duets", albumArtist = "Juniper Static", songCount = 3, year = 2021)
        val other = createAlbum(name = "Solo", albumArtist = "Juniper Static", year = 2019)
        robot.setAlbum(readyAlbumDetail(album = album, moreByArtist = listOf(other), moreByArtistNames = emptyList()))

        robot.scrollTo("More by this artist")
        robot.assertTextDisplayed("More by this artist")
    }

    @Test
    fun `More by is titled from the album artists, not the album's raw tag`() {
        val album = createAlbum(name = "Duets", albumArtist = "Juniper Static feat. Verdigris", songCount = 3, year = 2021)
        val other = createAlbum(name = "Solo", albumArtist = "Verdigris", year = 2019)
        robot.setAlbum(readyAlbumDetail(album = album, moreByArtist = listOf(other), moreByArtistNames = listOf("Juniper Static", "Verdigris")))

        robot.scrollTo("More by Juniper Static, Verdigris")
        robot.assertTextDisplayed("More by Juniper Static, Verdigris")
    }

    @Test
    fun `without other albums by the artist there is no More by shelf`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextNotDisplayed("More by", substring = true)
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setAlbum(loadingAlbumDetail)

        robot.assertTextNotDisplayed("Play")
    }

    @Test
    fun `an album that is gone says it is not in the library and navigates up`() {
        robot.setAlbum(missingAlbumDetail)

        robot.assertTextDisplayed("Not in your library")
        robot.clickNavigateUp()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `an album's overflow menu offers Go to album, which opens the album`() {
        val album = albumOf(phaseGardenSongs())
        var opened = false

        val target = albumMoreTarget(album, "Go to album") { opened = true }

        target.selection shouldBe MediaSelection.Albums(album)
        target.extraActions.map { it.label } shouldBe listOf("Go to album")
        target.extraActions.single().onClick()
        opened shouldBe true
    }
}
