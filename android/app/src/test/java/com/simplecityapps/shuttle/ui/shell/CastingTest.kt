package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.playback.CastDevice
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerTestTags
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The players while casting: each names the Cast device the song plays on (#795). */
@RunWith(RobolectricTestRunner::class)
class CastingTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    private val casting = shellQueue("First song").copy(castDevice = CastDevice("Living Room TV"))

    @Test
    fun `while casting the mini player names the Cast device in place of the artist`() {
        robot.setContent(queue = casting)

        robot.showsText("Playing on Living Room TV", PlayerTestTags.MiniPlayer) shouldBe true
        robot.showsText("Juniper Static", PlayerTestTags.MiniPlayer) shouldBe false
    }

    @Test
    fun `while casting Now Playing names the Cast device`() {
        robot.setContent(queue = casting)
        robot.tapMiniPlayer()
        robot.assertLevel(PlayerLevel.Full)

        robot.showsText("Playing on Living Room TV", PlayerTestTags.NowPlaying) shouldBe true
    }

    @Test
    fun `a Cast device the session doesn't name is still shown as one`() {
        robot.setContent(queue = shellQueue("First song").copy(castDevice = CastDevice(name = null)))

        robot.showsText("Playing on your Cast device", PlayerTestTags.MiniPlayer) shouldBe true
    }

    @Test
    fun `once casting stops the mini player shows the artist again`() {
        robot.setContent(queue = casting)
        robot.setQueue(casting.copy(castDevice = null))

        robot.showsText("Juniper Static", PlayerTestTags.MiniPlayer) shouldBe true
        robot.showsText("Playing on", PlayerTestTags.MiniPlayer) shouldBe false
    }
}
