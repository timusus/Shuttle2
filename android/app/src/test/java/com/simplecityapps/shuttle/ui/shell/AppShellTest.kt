package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createPlaylist
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.actions.SnackbarAction
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import io.kotest.matchers.collections.shouldContain
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
    fun `the compact nav bar shows every tab on screen`() {
        robot.setContent()
        listOf("Library", "Search", "More").forEach(robot::assertTextDisplayed)
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
        robot.assertLevel(PlayerLevel.NowPlaying)

        robot.setQueueMidAnimation(EmptyShellQueue)
        robot.assertSheetPresent()

        robot.settle()
        robot.assertSheetAbsent()
    }

    @Test
    fun `tapping the mini player expands, and the queue peek opens the queue`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.NowPlaying)

        robot.tapQueuePeek()
        robot.assertLevel(PlayerLevel.Queue)
    }

    @Test
    fun `back steps the sheet down one level at a time, then leaves it at Mini`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.pressBack()
        robot.assertLevel(PlayerLevel.NowPlaying)
        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    fun `only the layers a level shows are reachable by accessibility`() {
        robot.setContent()
        robot.assertReachable("Collapse player", reachable = false)
        robot.assertReachable("Second song", reachable = false)

        robot.tapMiniPlayer()
        robot.assertReachable("Collapse player", reachable = true)
        robot.assertReachable("Second song", reachable = false)

        robot.tapQueuePeek()
        robot.assertReachable("Second song", reachable = true)
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
    fun `at Medium width the sheet has no Queue level, so back goes straight to Mini`() {
        robot.setContent(window = MediumWindow)
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.NowPlaying)

        robot.pressBack()
        robot.assertLevel(PlayerLevel.Mini)
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `compact Queue becomes Now Playing at Medium width`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.setWindow(MediumWindow)
        robot.assertLevel(PlayerLevel.NowPlaying)
    }

    @Test
    fun `the level survives saved state restoration`() {
        val restoration = StateRestorationTester(composeTestRule)
        robot.setContent(restoration = restoration)
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        restoration.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        robot.assertLevel(PlayerLevel.Queue)
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
    fun `compact Queue maps to the pane's Queue`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.setWindow(PaneWindow)
        robot.assertPaneShown()
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    fun `the pane's queue keeps the playing song above the transport, and tapping it goes back to Now Playing`() {
        robot.setContent(window = PaneWindow)
        robot.tapMiniPlayer()
        robot.assertQueueHeadSong(shown = false)

        robot.tapQueuePeek()
        robot.assertQueueHeadSong(shown = true)

        robot.tapQueueHeadSong()
        robot.assertQueueHeadSong(shown = false)
        robot.assertTextDisplayed("Now playing")
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
        robot.assertTextDisplayed("3:00")
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
    fun `tapping a queue row skips to it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.tapText("Third song")
        robot.calls shouldBe listOf("skipToQueueItem(2)")
    }

    @Test
    fun `swiping a queue row away removes it`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.swipeAwayQueueRow("Second song")
        robot.calls shouldBe listOf("removeQueueItem(1)")
    }

    @Test
    fun `dragging a row's handle moves it down the queue`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.dragQueueRow("First song", rows = 2)
        robot.calls shouldBe listOf("moveQueueItem(0, after 2)")
    }

    @Test
    fun `holding a dragged row at the bottom of the queue scrolls it to the end`() {
        robot.setContent(queue = shellQueue(*Array(30) { "Song ${it + 1}" }))
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.holdFirstQueueRowAtBottom(holdMs = 10_000)
        robot.calls shouldBe listOf("moveQueueItem(0, after 29)")
    }

    @Test
    fun `long-pressing a queue row offers Play Next`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.longPressQueueRow("Third song")
        robot.tapText("Play Next")
        robot.calls shouldBe listOf("playNext(2)")
    }

    @Test
    fun `clearing the queue offers Undo`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.tapDescription("Clear Queue")
        robot.tapText("Undo")
        robot.calls shouldContain "clearQueue"
        robot.calls.last() shouldBe "undoClearQueue"
    }

    @Test
    fun `removing a queue row from its menu offers Undo`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.longPressQueueRow("Second song")
        robot.tapText("Remove from Queue")
        robot.tapText("Undo")
        robot.calls shouldBe listOf("removeQueueItem(1)", "undoRemoveQueueItem")
    }

    @Test
    fun `a queue row's song actions act on that row's song`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapQueuePeek()

        robot.longPressQueueRow("Third song")
        robot.tapText("Song Info")
        robot.actions.mediaActions.single().shouldBeSongAction<MediaAction.SongInfo>("Third song")
    }

    @Test
    fun `Go to album in the Now Playing menu opens the album and settles the sheet to Mini`() {
        val album = createAlbum(name = "Slow Bloom", albumArtist = "Ines Quarrow")
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
        val artist = createAlbumArtist(name = "Ines Quarrow")
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
    fun `the sleep timer sheet starts a preset or a slid length, playing the last song to the end if asked`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapDescription("Sleep timer")
        robot.tapText("15 min")
        robot.tapText("Play last song to end")
        robot.tapText("Start timer")

        robot.calls shouldContain "startSleepTimer(900000, true)"
        robot.assertTextDisplayed("15:00")
    }

    @Test
    fun `a running sleep timer counts down in the header until it stops`() {
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
    fun `the running sleep timer's sheet adds five minutes or stops it`() {
        robot.actions.sleepTimerRemaining.value = 90_000
        robot.setContent(queue = shellQueue("First song").copy(sleepTimerActive = true))
        robot.tapMiniPlayer()
        robot.tapSleepTimerChip()
        robot.tapText("Add 5 min")
        robot.calls shouldContain "startSleepTimer(390000, false)"

        robot.tapText("Stop Timer")
        robot.calls shouldContain "stopSleepTimer"
        robot.assertReachable("Stop Timer", reachable = false)
    }

    @Test
    fun `playback and sound sets the speed and ReplayGain, and a speed other than normal shows in the header`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapDescription("More options")
        robot.tapText("Playback & sound")
        robot.tapText("1.5×")
        robot.tapText("Album Gain")

        robot.calls shouldContain "setPlaybackSpeed(1.5)"
        robot.calls shouldContain "setReplayGainMode(Album)"
        robot.assertReachable("Playback speed 1.5×", reachable = true)

        robot.actions.setPlaybackSpeed(1f)
        robot.assertReachable("Playback speed 1×", reachable = false)
    }

    @Test
    fun `playback and sound links to the equalizer and the rest of its settings, settling the player first`() {
        robot.setContent()
        robot.tapMiniPlayer()
        robot.tapDescription("More options")
        robot.tapText("Playback & sound")
        robot.tapText("Equalizer")

        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Equalizer screen")

        robot.tapMiniPlayer()
        robot.tapDescription("More options")
        robot.tapText("Playback & sound")
        robot.tapText("More sound settings")
        robot.assertLevel(PlayerLevel.Mini)
        robot.assertTextDisplayed("Settings: PlaybackAndSound")
    }
}

private inline fun <reified T : MediaAction> MediaAction.shouldBeSongAction(title: String) {
    (this is T) shouldBe true
    (selection as MediaSelection.Songs).songs.single().name shouldBe title
}
