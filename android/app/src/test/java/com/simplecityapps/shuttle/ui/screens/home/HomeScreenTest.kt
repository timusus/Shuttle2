package com.simplecityapps.shuttle.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import io.kotest.matchers.collections.shouldContain
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
        robot.assertDescriptionNotShown("Shuffle all")
    }

    @Test
    fun `an empty library offers the library's own empty-state actions, when given the slot`() {
        var allowAccessRequested = 0
        var serverConnectRequested = 0
        robot.setContent(
            HomeScenarios.empty,
            emptyContent = { modifier ->
                LibraryEmptyScreen(
                    state = LibraryAvailability.Empty(MusicAccess.NotRequested),
                    onAllowAccess = { allowAccessRequested++ },
                    onOpenAppSettings = {},
                    onScan = {},
                    onConnectServer = { serverConnectRequested++ },
                    modifier = modifier,
                )
            },
        )

        robot.assertTextDisplayed("Allow access to music")
        robot.tapVisibleText("Allow access to music")
        robot.tapVisibleText("Connect a server")

        allowAccessRequested shouldBe 1
        serverConnectRequested shouldBe 1
    }

    @Test
    fun `a library shows its sections`() {
        robot.setContent(HomeScenarios.content)

        robot.assertTextDisplayed("Jump back in")
        robot.assertTextDisplayed("Pick up where you left off")
        robot.scrollTo("Saltmarsh Choir")
        robot.scrollTo("Heavy rotation")
        robot.scrollTo("What you've played most in the last 4 weeks")
        robot.scrollTo("Recently added")
        robot.scrollTo("The newest additions to your library")
        robot.scrollTo("Genre picks")
        robot.scrollTo("Genres you've been playing")
        robot.scrollTo(HomeScenarios.genres.first().name)
    }

    @Test
    fun `cold start shows recently added, genre picks, a prominent shuffle all and how home fills in`() {
        robot.setContent(HomeScenarios.unplayed)

        robot.assertTextNotShown("Jump back in")
        robot.assertTextDisplayed("Recently added")
        robot.assertTextDisplayed("Home learns from what you play: your albums, artists and genres show up here as you listen.")
        robot.scrollTo("Genre picks")
        robot.scrollTo("The biggest genres in your library")
        // The card's Shuffle all replaces the top bar's.
        robot.assertDescriptionNotShown("Shuffle all")
        robot.tapText("Shuffle all")

        robot.shuffles shouldBe 1
    }

    @Test
    fun `jump back in is a grid of two columns on a phone, at most four rows`() {
        robot.setContent(HomeScenarios.content)

        robot.gridColumns() shouldBe 2
        robot.gridCellCount() shouldBe JUMP_BACK_IN_MAXIMUM_ITEMS
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp-xhdpi")
    fun `jump back in is four columns wide from a medium window`() {
        robot.setContent(HomeScenarios.content)

        robot.gridColumns() shouldBe 4
    }

    @Test
    @Config(fontScale = 2f)
    fun `jump back in is one column at the largest font sizes`() {
        robot.setContent(HomeScenarios.content)

        robot.gridColumns() shouldBe 1
    }

    @Test
    fun `a mixed shelf says what each tile is`() {
        robot.setContent(HomeScenarios.content)

        robot.scrollTo("Rediscover")
        robot.scrollTo(HomeScenarios.lateNight.name)
        robot.assertTextDisplayed("Playlist · ${HomeScenarios.lateNight.songCount} songs")
    }

    @Test
    fun `a phone cell has no play button, its long-press offers Play`() {
        robot.setContent(HomeScenarios.content)

        robot.assertDescriptionNotShown("Play Phase Garden")
        robot.customActionLabels("Phase Garden") shouldContain "Play"
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp-xhdpi")
    fun `a wide cell's play button plays its item with the item's context`() {
        robot.setContent(HomeScenarios.content)
        val phaseGarden = HomeItem.AlbumItem(HomeScenarios.phaseGarden)

        robot.tapDescription("Play Phase Garden")

        robot.actions shouldContainExactly listOf(phaseGarden.resumeAction())
        (robot.actions.single() as MediaAction.Resume).context shouldBe phaseGarden.playContext
        robot.openedItems shouldBe emptyList()
    }

    @Test
    fun `a jump back in cell names the song its queue was left on`() {
        robot.setContent(HomeScenarios.resuming)

        robot.assertTextDisplayed("Glasshouse")
    }

    @Test
    fun `a jump back in cell's Play resumes, and Play from start plays it in order`() {
        robot.setContent(HomeScenarios.content)
        val phaseGarden = HomeItem.AlbumItem(HomeScenarios.phaseGarden)

        robot.customActionLabels("Phase Garden") shouldContainExactly listOf("Play", "Play from start", "Shuffle", "Play Next", "Add to Queue", "Go to album")
        robot.performCustomAction("Phase Garden", "Play")
        robot.performCustomAction("Phase Garden", "Play from start")

        robot.actions shouldContainExactly listOf(MediaAction.Resume(phaseGarden.playInOrderAction(), phaseGarden.playContext), phaseGarden.playInOrderAction())
    }

    @Test
    fun `a jump back in cell's long-press actions resume on Play, with Play from start`() {
        robot.setContent(HomeScenarios.content)
        val phaseGarden = HomeItem.AlbumItem(HomeScenarios.phaseGarden)

        robot.longPressText("Phase Garden")

        val target = robot.shownActions.single()
        target.playAction shouldBe MediaAction.Resume(phaseGarden.playInOrderAction(), phaseGarden.playContext)
        target.playFromStart!!.label shouldBe "Play from start"
        target.playFromStart!!.onClick()
        robot.actions shouldContainExactly listOf(phaseGarden.playInOrderAction())
    }

    @Test
    fun `other tiles play from the start, with no Play from start`() {
        robot.setContent(HomeScenarios.content)

        robot.longPressText("Soft Focus")

        robot.shownActions.single().playAction shouldBe null
        robot.shownActions.single().playFromStart shouldBe null
    }

    @Test
    fun `a smart playlist keeps its own tag and plays with its context`() {
        robot.setContent(HomeScenarios.content)

        robot.scrollTo("Favorites")
        robot.assertTagCount("homeTile.smartPlaylist", 1)
        robot.performCustomAction("Favorites", "Play")

        (robot.actions.single() as MediaAction.Play).context shouldBe HomeScenarios.smartPlaylist.playContext
    }

    @Test
    fun `tapping a cell or tile opens it, and a genre tile shuffles the genre`() {
        robot.setContent(HomeScenarios.content)
        val genre = HomeScenarios.genres.first()

        robot.tapText("Harbour Weather")
        robot.tapText("Saltmarsh Choir")
        robot.tapText("Soft Focus")
        robot.tapText(genre.name)

        robot.openedItems shouldContainExactly listOf(
            HomeItem.AlbumItem(HomeScenarios.harbourWeather),
            HomeItem.ArtistItem(HomeScenarios.saltmarshChoir),
            HomeItem.AlbumItem(HomeScenarios.softFocus),
        )
        robot.actions shouldContainExactly listOf(HomeItem.GenreItem(genre).playAction())
    }

    @Test
    fun `long-pressing a tile opens its actions, with its context and a way to it`() {
        robot.setContent(HomeScenarios.content)

        robot.longPressText("Soft Focus")

        val target = robot.shownActions.single()
        target.selection shouldBe MediaSelection.Albums(HomeScenarios.softFocus)
        target.playContext shouldBe HomeItem.AlbumItem(HomeScenarios.softFocus).playContext
        target.extraActions.map { it.label } shouldContainExactly listOf("Go to album")
    }

    @Test
    fun `long-pressing a grid cell opens its actions too`() {
        robot.setContent(HomeScenarios.content)

        robot.longPressText("Saltmarsh Choir")

        robot.shownActions.single().selection shouldBe MediaSelection.AlbumArtists(HomeScenarios.saltmarshChoir)
    }

    @Test
    fun `talkback offers each tile's actions`() {
        robot.setContent(HomeScenarios.content)

        robot.customActionLabels("Soft Focus") shouldContainExactly listOf("Play", "Shuffle", "Play Next", "Add to Queue", "Go to album")
        robot.performCustomAction("Soft Focus", "Shuffle")

        robot.actions.single() shouldBe MediaAction.Shuffle(MediaSelection.Albums(HomeScenarios.softFocus), HomeItem.AlbumItem(HomeScenarios.softFocus).playContext)
    }

    @Test
    fun `recently added offers see all, and the other sections don't`() {
        robot.setContent(HomeScenarios.content)

        robot.tapText("See all")

        robot.seeAlls shouldContainExactly listOf(HomeSectionId.RecentlyAdded)
        HomeSectionId.entries.filter { it.hasSeeAll } shouldContainExactly listOf(HomeSectionId.RecentlyAdded)
    }

    @Test
    fun `shuffle all and settings are wired from the top bar`() {
        robot.setContent(HomeScenarios.content)

        robot.tapDescription("Shuffle all")
        robot.tapDescription("Settings")

        robot.shuffles shouldBe 1
        robot.settingsOpened shouldBe 1
    }

    @Test
    fun `pulling the list down refreshes home`() {
        robot.setContent(HomeScenarios.content)

        robot.pullToRefresh()

        robot.refreshes shouldBe 1
    }

    @Test
    fun `home has its title and no search button, which the Search tab covers`() {
        robot.setContent(HomeScenarios.content)

        robot.assertTextDisplayed("Home")
        robot.assertDescriptionNotShown("Search")
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

        robot.assertTextNotShown("What's new in Shuttle Music")
    }
}
