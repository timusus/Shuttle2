package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
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
        robot.tapText("Album 1")
        robot.assertTextDisplayed("Track 1")

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
}
