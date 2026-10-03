package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.simplecityapps.createPlaylist
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.actions.SnackbarAction
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingPanel
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.S2RepeatMode
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AppShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    @Test
    fun `a queue reveals the mini player`() {
        robot.setContent()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    fun `the compact nav bar shows every tab on screen, and nothing else`() {
        robot.setContent()
        listOf("Library", "Search").forEach(robot::assertTextDisplayed)
        robot.assertReachable("More", reachable = false)
    }

    @Test
    @Config(qualifiers = "de")
    fun `the nav bar's tab labels are translated`() {
        robot.setContent()
        robot.assertTextDisplayed("Bibliothek")
    }

    @Test
    fun `an empty queue composes no sheet`() {
        robot.setContent(queue = EmptyShellQueue)
        robot.assertSheetAbsent()
    }

    @Test
    fun `a queue arriving reveals the sheet and emptying it hides the sheet`() {
        robot.setContent(queue = EmptyShellQueue)
        robot.setQueue(shellQueue("First song"))
        robot.assertLevel(PlayerLevel.Mini)

        robot.tapMiniPlayer()
        robot.setQueue(EmptyShellQueue)
        robot.assertSheetAbsent()
    }

    @Test
    fun `emptying the queue under the expanded sheet slides it away rather than cutting it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.Full)

        robot.setQueueMidAnimation(EmptyShellQueue)
        robot.assertSheetPresent()

        robot.settle()
        robot.assertSheetAbsent()
    }

    @Test
    fun `tapping the mini player opens the full player, and dragging it down collapses it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.Full)
        robot.assertPanel(null)
        robot.assertTransportDisplayed()

        robot.swipeDownNowPlaying()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    fun `the queue button opens the queue under the pushed-up transport`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapPanelButton(NowPlayingPanel.Queue)
        robot.assertLevel(PlayerLevel.Full)
        robot.panel shouldBe NowPlayingPanel.Queue
        robot.assertPanel(NowPlayingPanel.Queue)
        robot.assertTransportDisplayed()
    }

    @Test
    fun `each bar button opens its panel, and tapping it again closes it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        listOf(NowPlayingPanel.SleepTimer, NowPlayingPanel.PlaybackSound, NowPlayingPanel.Queue).forEach { panel ->
            robot.tapPanelButton(panel)
            robot.assertLevel(PlayerLevel.Full)
            robot.panel shouldBe panel
            robot.assertPanel(panel)

            robot.tapPanelButton(panel)
            robot.assertLevel(PlayerLevel.Full)
            robot.panel shouldBe null
            robot.assertPanel(null)
        }
    }

    @Test
    fun `swiping up on the full player opens no panel`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.swipeUpNowPlaying()
        robot.assertLevel(PlayerLevel.Full)
        robot.panel shouldBe null
        robot.assertPanel(null)
    }

    @Test
    fun `swiping up on the bar opens the queue`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.swipeUpBar()
        robot.assertLevel(PlayerLevel.Full)
        robot.panel shouldBe NowPlayingPanel.Queue
    }

    @Test
    fun `dragging the bar down collapses the full player`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.swipeDownBar()
        robot.assertLevel(PlayerLevel.Mini)
        robot.panel shouldBe null
    }

    @Test
    fun `dragging the queue down closes it and leaves the full player`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.swipeDownPanel()
        robot.panel shouldBe null
        robot.assertLevel(PlayerLevel.Full)
    }

    @Test
    fun `back closes the open panel first, then collapses the player to Mini`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.pressBack()
        robot.assertLevel(PlayerLevel.Full)
        robot.panel shouldBe null
        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    fun `collapsing the player closes its panel`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)
        robot.swipeDownTransport()

        robot.assertLevel(PlayerLevel.Mini)
        robot.panel shouldBe null
    }

    @Test
    fun `only the layers a level shows are reachable by accessibility`() {
        robot.setContent()
        robot.assertReachable("Collapse player", reachable = false)
        robot.assertReachable("Second song", reachable = false)

        robot.tapMiniPlayer()
        robot.assertReachable("Collapse player", reachable = true)

        robot.tapPanelButton(NowPlayingPanel.Queue)
        robot.assertReachable("Second song", reachable = true)
        // The panel sheet's grip is the one handle while a panel is open.
        robot.assertReachable("Collapse player", reachable = false)
    }

    @Test
    fun `home is hidden while another tab or a pushed screen covers it, and shown again on return (#672)`() {
        robot.setContent()
        robot.tapText("Library")
        robot.tapText("Home")
        robot.tapText("Phase Garden")
        robot.pressBack()

        robot.homeVisibility shouldBe listOf(true, false, true, false, true)
    }

    @Test
    fun `back at Mini pops the destination instead`() {
        robot.setContent()
        robot.tapText("Phase Garden")
        robot.assertTextDisplayed("Chlorophyll Loop")

        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Recently played")
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `at Medium width back collapses the full player to Mini`() {
        robot.setContent(window = MediumWindow)
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.Full)

        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `at Medium width a panel opens beside the player only on its button, and back closes it before the sheet`() {
        robot.setContent(window = MediumWindow)
        robot.tapMiniPlayer()
        robot.assertPanel(null)
        robot.tapPanelButton(NowPlayingPanel.Queue)
        robot.assertLevel(PlayerLevel.Full)
        robot.assertPanel(NowPlayingPanel.Queue)

        robot.pressBack()
        robot.assertLevel(PlayerLevel.Full)
        robot.panel shouldBe null
        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `at Medium width a panel opening beside the player leaves the player's menu open`() {
        robot.setContent(window = MediumWindow)
        robot.tapMiniPlayer()
        robot.tapDescription("More options")

        robot.showPanel(NowPlayingPanel.Queue)
        robot.tapText("Clear Queue")
        robot.calls shouldBe listOf("clearQueue")
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `the compact full player keeps its open panel at Medium width`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)

        robot.setWindow(MediumWindow)
        robot.assertLevel(PlayerLevel.Full)
        robot.assertPanel(NowPlayingPanel.SleepTimer)
    }

    @Test
    fun `on a phone song info opens in a bottom sheet over the destination, with the player at Mini`() {
        robot.setContent()
        robot.openSongInfo()

        robot.assertSongInfo(inSheet = true)
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Recently played")
    }

    @Test
    fun `back in the song info sheet dismisses it, leaving the destination and the player at Mini`() {
        robot.setContent()
        robot.openSongInfo()

        robot.pressBackInSheet()
        robot.assertSongInfoAbsent()
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Recently played")
    }

    @Test
    fun `a screen opened over the song info sheet slides the sheet away rather than cutting it`() {
        robot.setContent()
        robot.openSongInfo()

        robot.navigateMidAnimation(NavigationTarget.Album(SampleLibrary.albums.first { it.title == "Slow Bloom" }.toAlbum()))
        robot.assertSongInfoSheetPresent()

        robot.settle()
        robot.assertSongInfoAbsent()
        robot.assertTextDisplayed("Morning Glory")
    }

    @Test
    fun `the song info sheet reopens after saved state restoration`() {
        val restoration = StateRestorationTester(composeTestRule)
        robot.setContent(restoration = restoration)
        robot.openSongInfo()

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        robot.assertSongInfo(inSheet = true)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `from 600 dp song info stays a detail pane beside the list`() {
        robot.setContent(window = windowInfo(840, 900))
        robot.tapText("Library")
        robot.openSongInfo()

        robot.assertSongInfo(inSheet = false)
        robot.assertTextDisplayed("Albums")
    }

    @Test
    fun `the level survives saved state restoration`() {
        val restoration = StateRestorationTester(composeTestRule)
        robot.setContent(restoration = restoration)
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        robot.assertLevel(PlayerLevel.Full)
        robot.assertPanel(NowPlayingPanel.Queue)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `growing to pane width opens the pane, and shrinking back leaves the mini player`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.setWindow(PaneWindow)
        robot.assertPaneShown()
        robot.assertSheetAbsent()

        robot.setWindow(CompactWindow)
        robot.assertPaneAbsent()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the compact full player opens the pane on the open panel`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)

        robot.setWindow(PaneWindow)
        robot.assertPaneShown()
        robot.assertPanel(NowPlayingPanel.SleepTimer)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the pane's bar opens and closes each panel`() {
        robot.setContent(window = PaneWindow)
        robot.tapMiniPlayer()
        robot.assertPaneShown()
        robot.assertPanel(null)

        robot.tapPanelButton(NowPlayingPanel.Queue)
        robot.assertPanel(NowPlayingPanel.Queue)
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        robot.assertPanel(NowPlayingPanel.PlaybackSound)
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        robot.panel shouldBe null
        robot.assertPanel(null)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `back in the pane closes its open panel and leaves the pane open`() {
        robot.setContent(window = PaneWindow)
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.pressBack()
        robot.panel shouldBe null
        robot.assertPanel(null)
        robot.assertPaneShown()
    }

    @Test
    fun `now playing plays and pauses, skips and seeks`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapPlayerControl("Play")
        robot.tapPlayerControl("Pause")
        robot.tapPlayerControl("Next")
        robot.tapPlayerControl("Previous")
        robot.seekTo(0.5f)

        robot.calls shouldBe listOf("togglePlayback", "togglePlayback", "skipToNext", "skipToPrevious", "seekTo(90000)")
    }

    @Test
    fun `now playing shows the song and its position`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertTextDisplayed("Juniper Static • Phase Garden")
        robot.assertTextDisplayed("1:00")
        robot.assertTextDisplayed("-2:00", outsideQueue = true)
    }

    @Test
    fun `tapping the end time switches between the time left and the song's length`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertTextDisplayed("-2:00", outsideQueue = true)

        robot.tapTextOutsideQueue("-2:00")
        robot.assertTextDisplayed("3:00", outsideQueue = true)
        robot.tapTextOutsideQueue("3:00")
        robot.assertTextDisplayed("-2:00", outsideQueue = true)

        robot.calls shouldBe listOf("setShowRemainingTime(false)", "setShowRemainingTime(true)")
    }

    @Test
    fun `the mini player plays in place, and swipes sideways to skip`() {
        robot.setContent()
        robot.tapDescription("Play")
        robot.swipeMiniPlayer(towardsStart = true)
        robot.swipeMiniPlayer(towardsStart = false)

        robot.calls shouldBe listOf("togglePlayback", "skipToNext", "skipToPrevious")
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    fun `swiping the now playing artwork skips, and leaves the sheet at rest`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.swipeNowPlayingArtwork(towardsStart = true)
        robot.swipeNowPlayingArtwork(towardsStart = false)

        robot.calls shouldBe listOf("skipToNext", "skipToPrevious")
        robot.assertLevel(PlayerLevel.Full)
    }

    @Test
    fun `the mini player's next button skips`() {
        robot.setContent()
        robot.tapDescription("Next")

        robot.calls shouldBe listOf("skipToNext")
    }

    @Test
    fun `the mini player seeks forward for an audiobook instead of skipping`() {
        robot.setContent(queue = audiobookShellQueue(), progress = PlayerProgress(60_000, 180_000))
        robot.tapDescription("Seek forward")

        robot.calls shouldBe listOf("seekTo(90000)")
    }

    @Test
    fun `now playing shows seek buttons for a podcast, not skip`() {
        robot.setContent(queue = audiobookShellQueue(), progress = PlayerProgress(60_000, 180_000))
        robot.tapMiniPlayer()

        robot.tapPlayerControl("Seek backward")
        robot.tapPlayerControl("Seek forward")

        robot.calls shouldBe listOf("seekTo(50000)", "seekTo(90000)")
    }

    @Test
    fun `holding the now playing next button repeats seeking forward instead of skipping`() {
        robot.setContent(progress = PlayerProgress(60_000, 180_000))
        robot.tapMiniPlayer()

        composeTestRule.mainClock.autoAdvance = false
        robot.holdPlayerControl("Next")
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(700L)
        composeTestRule.mainClock.advanceTimeBy(700L)
        robot.releasePlayerControl("Next")

        // The fake doesn't advance progress between calls, so each hold-tick seeks 15s from the same fixed 60s base.
        robot.calls shouldBe listOf("seekTo(75000)", "seekTo(75000)")
    }

    @Test
    fun `the mini player shows how far through the song it is`() {
        robot.setContent(progress = PlayerProgress(60_000, 180_000))

        robot.miniPlayerProgress() shouldBe (1f / 3 plusOrMinus 0.001f)
    }

    @Test
    fun `now playing toggles shuffle, cycles repeat and toggles the favourite`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapPlayerControl("Shuffle")
        robot.tapPlayerControl("Repeat off")
        robot.tapPlayerControl("Favorite")

        robot.calls shouldBe listOf("toggleShuffle", "cycleRepeatMode", "toggleFavourite")
    }

    @Test
    fun `now playing shows each of the three repeat modes`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.setQueue(shellQueue("First song").copy(repeatMode = S2RepeatMode.All))
        robot.assertReachable("Repeat all", reachable = true)
        robot.setQueue(shellQueue("First song").copy(repeatMode = S2RepeatMode.One))
        robot.assertReachable("Repeat one", reachable = true)
        robot.setQueue(shellQueue("First song").copy(repeatMode = S2RepeatMode.Off))
        robot.assertReachable("Repeat off", reachable = true)
    }

    @Test
    fun `now playing shows whether shuffle is on and the song is a favourite`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertToggle("Shuffle", on = false)
        robot.assertToggle("Favorite", on = false)

        robot.setQueue(shellQueue("First song").copy(shuffle = true, favourite = true))
        robot.assertToggle("Shuffle", on = true)
        robot.assertToggle("Favorite", on = true)
    }

    @Test
    fun `opening the queue scrolls to the songs after the current one`() {
        robot.setContent(queue = shellQueue(*Array(30) { "Song ${it + 1}" }, playing = 20))
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.assertQueueRowDisplayed("Song 22", displayed = true)
        robot.assertQueueRowDisplayed("Song 1", displayed = false)
    }

    @Test
    fun `a queue opened before its songs load scrolls to the current one once they do`() {
        val queue = shellQueue(*Array(30) { "Song ${it + 1}" }, playing = 20)
        robot.setContent(queue = queue.copy(items = emptyList()))
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.setQueue(queue.copy(panel = NowPlayingPanel.Queue))
        robot.assertQueueRowDisplayed("Song 22", displayed = true)
        robot.assertQueueRowDisplayed("Song 1", displayed = false)
    }

    @Test
    fun `while a panel slides up its rows are already reachable and the handle is not`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapPanelButtonMidAnimation(NowPlayingPanel.Queue)
        robot.assertReachable("Second song", reachable = true)
        robot.assertReachable("Collapse player", reachable = false)
        // The song it pushes up is already out of the semantics tree; the transport stays.
        robot.assertNowPlayingSongReachable(false)
        robot.settle()
        robot.assertTransportDisplayed()
    }

    @Test
    fun `an accessibility service closes a panel from its grip, bringing the song back`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)

        robot.dismissPanelByAccessibility()
        robot.panel shouldBe null
        robot.assertLevel(PlayerLevel.Full)
        robot.assertNowPlayingSongReachable(true)
    }

    @Test
    fun `tapping a queue row skips to it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.tapText("Third song")
        robot.calls shouldBe listOf("skipToQueueItem(2)")
    }

    @Test
    fun `swiping a queue row away removes it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.swipeAwayQueueRow("Second song")
        robot.calls shouldBe listOf("removeQueueItem(1)")
    }

    @Test
    fun `dragging a row's handle moves it down the queue`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.dragQueueRow("First song", rows = 2)
        robot.calls shouldBe listOf("moveQueueItem(0, after 2)")
    }

    @Test
    fun `holding a dragged row at the bottom of the queue scrolls it to the end`() {
        robot.setContent(queue = shellQueue(*Array(30) { "Song ${it + 1}" }))
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.holdFirstQueueRowAtBottom(holdMs = 10_000)
        robot.calls shouldBe listOf("moveQueueItem(0, after 29)")
    }

    @Test
    fun `long-pressing a queue row offers Play Next`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.longPressQueueRow("Third song")
        robot.tapText("Play Next")
        robot.calls shouldBe listOf("playNext(2)")
    }

    @Test
    fun `clearing the queue offers Undo`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.tapDescription("More options")
        robot.tapText("Clear Queue")
        robot.tapText("Undo")
        robot.calls shouldContain "clearQueue"
        robot.calls.last() shouldBe "undoClearQueue"
    }

    @Test
    fun `removing a queue row from its menu offers Undo`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.longPressQueueRow("Second song")
        robot.tapText("Remove from Queue")
        robot.tapText("Undo")
        robot.calls shouldBe listOf("removeQueueItem(1)", "undoRemoveQueueItem")
    }

    @Test
    fun `a queue row's song actions act on that row's song`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.Queue)

        robot.longPressQueueRow("Third song")
        robot.tapText("Song Info")
        robot.actions.mediaActions.single().shouldBeSongAction<MediaAction.SongInfo>("Third song")
    }

    @Test
    fun `Go to album in the Now Playing menu opens the album and settles the sheet to Mini`() {
        val album = SampleLibrary.albums.first { it.title == "Slow Bloom" }.toAlbum()
        robot.actions.mediaActionResult = { MediaActionResult.Navigate(NavigationTarget.Album(album)) }
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Go to album")
        robot.actions.mediaActions.single().shouldBeSongAction<MediaAction.GoToAlbum>("First song")
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Morning Glory")
    }

    @Test
    fun `Go to artist in the Now Playing menu opens the artist and settles the sheet to Mini`() {
        val artist = SampleLibrary.artists.first { it.name == "Ines Quarrow" }.toAlbumArtist()
        robot.actions.mediaActionResult = { MediaActionResult.Navigate(NavigationTarget.AlbumArtist(artist)) }
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Go to artist")
        robot.actions.mediaActions.single().shouldBeSongAction<MediaAction.GoToArtist>("First song")
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Slow Bloom")
    }

    @Test
    fun `the Now Playing menu keeps Clear Queue after the song actions`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Clear Queue")
        robot.calls shouldBe listOf("clearQueue")
        robot.actions.mediaActions shouldBe emptyList()
    }

    @Test
    fun `Add to playlist adds the song to the playlist picked`() {
        val roadTrip = createPlaylist(name = "Road trip")
        robot.actions.playlists = listOf(roadTrip)
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Add to Playlist")
        robot.assertTextDisplayed("New Playlist")
        robot.tapText("Road trip")

        val added = robot.actions.mediaActions.single()
        added.shouldBeSongAction<MediaAction.AddToPlaylist>("First song")
        (added as MediaAction.AddToPlaylist).playlist shouldBe roadTrip
    }

    @Test
    fun `Save Queue to Playlist adds the queue to the playlist picked`() {
        val roadTrip = createPlaylist(name = "Road trip")
        robot.actions.playlists = listOf(roadTrip)
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Save Queue to Playlist")
        robot.tapText("Road trip")

        robot.actions.mediaActions shouldBe listOf(MediaAction.AddToPlaylist(MediaSelection.Queue, roadTrip))
    }

    @Test
    fun `a song action's snackbar button sends its action back`() {
        val include = MediaAction.Include(MediaSelection.Songs(emptyList()))
        robot.actions.mediaActionResult = { action ->
            if (action is MediaAction.Exclude) MediaActionResult.Message(MediaActionMessage.Excluded(1), SnackbarAction(SnackbarAction.Label.Undo, include)) else MediaActionResult.None
        }
        robot.setContent()
        robot.tapMiniPlayer()

        robot.tapDescription("More options")
        robot.tapText("Exclude")
        robot.assertTextDisplayed("1 song excluded")
        robot.tapText("Undo")
        robot.actions.mediaActions.last() shouldBe include
    }

    @Test
    fun `the sleep timer panel starts a slid length, playing the last song to the end if asked`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)
        robot.setRuler("Length", 2)
        robot.tapText("Play last song to end")
        robot.tapText("Start timer")

        robot.calls shouldContain "startSleepTimer(900000, true)"
        robot.assertReachable("15:00", reachable = true)
        robot.assertTextDisplayed("Stop Timer")
    }

    @Test
    fun `End of song starts a timer that stops after the playing song`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)
        robot.tapText("End of song")

        robot.calls shouldContain "startSleepTimer(0, true)"
    }

    @Test
    fun `a running sleep timer counts down in the bar until it stops`() {
        robot.actions.sleepTimerRemaining.value = 754_000
        robot.setContent(queue = shellQueue("First song").copy(sleepTimerActive = true))
        robot.tapMiniPlayer()
        robot.assertTextDisplayed("12:34")

        robot.actions.sleepTimerRemaining.value = 0
        robot.setQueue(shellQueue("First song").copy(sleepTimerActive = true))
        robot.assertTextDisplayed("End of song")

        robot.actions.stopSleepTimer()
        robot.setQueue(shellQueue("First song"))
        robot.assertReachable("End of song", reachable = false)
        robot.assertReachable("Sleep timer", reachable = true)
    }

    @Test
    fun `the running sleep timer's panel adds five minutes or stops it`() {
        robot.actions.sleepTimerRemaining.value = 90_000
        robot.setContent(queue = shellQueue("First song").copy(sleepTimerActive = true))
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)
        robot.tapText("Add 5 min")
        robot.calls shouldContain "startSleepTimer(390000, false)"

        robot.tapText("Stop Timer")
        robot.calls shouldContain "stopSleepTimer"
        robot.assertReachable("Stop Timer", reachable = false)
    }

    @Test
    fun `playback and sound sets the speed and ReplayGain, and a speed other than normal shows in the bar`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        robot.tapText("1.5×")
        robot.tapText("Album Gain")

        robot.calls shouldContain "setPlaybackSpeed(1.5)"
        robot.calls shouldContain "setReplayGainMode(Album)"
        robot.assertReachable("Playback & sound, Playback speed 1.5×", reachable = true)

        robot.actions.setPlaybackSpeed(1f)
        robot.assertReachable("Playback & sound, Playback speed 1×", reachable = false)
    }

    @Test
    fun `a settings screen hides the nav bar and lights no tab, keeping the mini player docked, and back restores the tab`() {
        robot.setContent()
        robot.tapText("Library")
        robot.tapText(SampleLibrary.albums.first().title)
        robot.miniPlayerGapToBottom() shouldBeGreaterThan 0.dp

        robot.openSoundSettings()
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
        robot.assertSelectedTab(null)
        robot.assertLevel(PlayerLevel.Mini)
        robot.miniPlayerGapToBottom() shouldBe 0.dp

        robot.pressBack()
        robot.assertSelectedTab("Library")
        robot.assertTextDisplayed(SampleLibrary.albums.first().title)
        robot.miniPlayerGapToBottom() shouldBeGreaterThan 0.dp
    }

    @Test
    fun `a settings screen on top survives saved state restoration, still without the nav bar`() {
        val restoration = StateRestorationTester(composeTestRule)
        robot.setContent(restoration = restoration)
        robot.tapText("Library")
        robot.openSoundSettings()

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
        robot.assertSelectedTab(null)
        robot.miniPlayerGapToBottom() shouldBe 0.dp

        robot.pressBack()
        robot.assertSelectedTab("Library")
    }

    @Test
    fun `a shortcut to another tab while settings is open leaves settings behind`() {
        robot.setContent()
        robot.tapText("Library")
        robot.tapText(SampleLibrary.albums.first().title)
        robot.openSoundSettings()

        robot.requestTab(ShellTab.Search)
        robot.assertSelectedTab("Search")

        // Library comes back as it was under settings, with the bar.
        robot.tapText("Library")
        robot.assertSelectedTab("Library")
        robot.assertTextDisplayed(SampleLibrary.albums.first().title)
        robot.miniPlayerGapToBottom() shouldBeGreaterThan 0.dp
    }

    @Test
    fun `a shortcut to the tab settings is open on pops that tab to its root`() {
        robot.setContent()
        robot.tapText("Library")
        robot.openSoundSettings()

        robot.requestTab(ShellTab.Library)
        robot.assertSelectedTab("Library")
        robot.assertTextDisplayed("Albums")
    }

    @Test
    fun `while the nav bar slides away the mini player rides its top edge, stopping on the gesture bar`() {
        robot.setContent(systemBars = PhoneSystemBars)
        robot.openSoundSettingsHoldingTheSlide()

        val tops = (1..4).map {
            robot.advanceFrames(2)
            (robot.miniPlayerBottom() - robot.navBarTop()).value shouldBe (0f plusOrMinus 1f)
            robot.navBarTop()
        }
        tops.distinct().size shouldBeGreaterThan 1
        robot.settle()
        robot.miniPlayerGapToBottom().value shouldBe (PhoneSystemBars.navigationBarDp.toFloat() plusOrMinus 1f)
    }

    @Test
    fun `at Medium width a settings screen pads itself clear of a cutout the hidden rail leaves bare`() {
        robot.setContent(window = MediumWindow, systemBars = SystemBars(statusBarDp = 0, navigationBarDp = 0, leftCutoutDp = 48))
        robot.openSoundSettings()

        robot.assertSelectedTab(null)
        robot.textLeft("Settings: PlaybackAndSound") shouldBeGreaterThanOrEqualTo 48.dp
    }

    @Test
    fun `at Medium width a settings screen hides the rail, and back brings it back`() {
        robot.setContent(window = MediumWindow)
        robot.assertSelectedTab("Home")

        robot.openSoundSettings()
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
        robot.assertSelectedTab(null)

        robot.pressBack()
        robot.assertSelectedTab("Home")
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `at pane width a settings screen hides the rail and leaves the pane open`() {
        robot.setContent(window = PaneWindow)
        robot.openSoundSettings()
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
        robot.assertSelectedTab(null)
        robot.assertPaneShown()

        // The pane's Playback & sound panel is still open, and back closes it first.
        robot.pressBack()
        robot.assertSelectedTab(null)
        robot.pressBack()
        robot.assertSelectedTab("Home")
    }

    @Test
    fun `playback and sound links to the equalizer and the rest of its settings, settling the player first`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        robot.tapText("Equalizer")

        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Equalizer screen")

        robot.tapMiniPlayer()
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        robot.scrollToAndTapText("More sound settings")
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
    }
}

private inline fun <reified T : MediaAction> MediaAction.shouldBeSongAction(title: String) {
    (this is T) shouldBe true
    (selection as MediaSelection.Songs).songs.single().name shouldBe title
}
