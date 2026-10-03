package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayerHeight
import com.simplecityapps.shuttle.ui.shell.player.PlayerTestTags
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The player's chrome at 200% text, where fixed heights used to cut lines through their glyphs (#730). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class LargeTextTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    @Test
    @Config(fontScale = 2f)
    fun `the mini player keeps its height, shows the title whole and speaks the artist`() {
        robot.setContent()

        robot.miniPlayerHeight() shouldBe S2MiniPlayerHeight
        robot.textClipping("First song", PlayerTestTags.MiniPlayer) shouldBe null
        robot.showsText("Juniper Static", PlayerTestTags.MiniPlayer) shouldBe false
        robot.speaksInside("First song, Juniper Static", PlayerTestTags.MiniPlayer) shouldBe true
    }

    @Test
    fun `at the default text size the mini player shows the artist under the title`() {
        robot.setContent()

        robot.miniPlayerHeight() shouldBe S2MiniPlayerHeight
        robot.textClipping("Juniper Static", PlayerTestTags.MiniPlayer) shouldBe null
    }

    @Test
    @Config(fontScale = 2f)
    fun `Now Playing's elapsed and remaining times draw whole`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.textClipping("1:00", PlayerTestTags.Transport) shouldBe null
        robot.textClipping("-2:00", PlayerTestTags.Transport) shouldBe null
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi", fontScale = 2f)
    fun `on a short phone Now Playing's times still draw whole`() {
        robot.setContent()
        robot.tapMiniPlayer()

        robot.textClipping("1:00", PlayerTestTags.Transport) shouldBe null
        robot.textClipping("-2:00", PlayerTestTags.Transport) shouldBe null
    }
}
