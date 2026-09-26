package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.SampleArtworkGlide
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingItems
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingPanel
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the shell at phone, foldable and tablet sizes into `docs/design/shell/` for review
 * (#375). A no-op under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*ShellScreenshotTest*'`. The player shows a
 * sample-library queue with its generated covers ([SampleArtworkGlide]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    @Before
    fun installSampleArtwork() = SampleArtworkGlide.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkGlide.uninstall()

    // The whole screen: with system bars the shell sits in a second compose root (the insets override).
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        composeTestRule.waitForIdle()
        captureScreenRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    private fun levels(prefix: String) {
        shot("$prefix-mini")
        robot.tapMiniPlayer()
        shot("$prefix-now-playing")
        robot.pressBack()
    }

    /** A compact sheet: Mini, the rest, each panel open, and the list scrolled past the artwork. */
    private fun sheetLevels(prefix: String) {
        shot("$prefix-mini")
        robot.tapMiniPlayer()
        shot("$prefix-now-playing")
        NowPlayingPanel.entries.forEach { panel ->
            robot.tapPanelButton(panel)
            shot("$prefix-${panel.shotName}")
            robot.tapPanelButton(panel)
        }
        robot.swipeUpNowPlaying()
        robot.scrollNowPlayingTo(NowPlayingItems.Title)
        shot("$prefix-artwork-scrolled")
        robot.backToMini()
    }

    private val NowPlayingPanel.shotName: String
        get() = when (this) {
            NowPlayingPanel.Queue -> "queue"
            NowPlayingPanel.SleepTimer -> "sleep-timer"
            NowPlayingPanel.PlaybackSound -> "playback-sound"
        }

    private fun libraryDetail(prefix: String) {
        robot.tapText("Library")
        robot.tapText("Night Bus Frequencies")
        shot("$prefix-library-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phone() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        sheetLevels("phone")
        libraryDetail("phone")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xhdpi")
    fun phoneDark() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        robot.tapMiniPlayer()
        shot("phone-dark-now-playing")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneShort() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(360, 640), systemBars = PhoneSystemBars)
        sheetLevels("phone-short")
    }

    @Test
    @Config(qualifiers = "w411dp-h826dp-xhdpi")
    fun foldableFolded() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 826), systemBars = PhoneSystemBars)
        sheetLevels("foldable-folded")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun flipTabletop() {
        // Half-opened flat on a table: a separating horizontal hinge across the middle, in window pixels.
        val hinge = HingeInfo(bounds = Rect(0f, 889f, 822f, 893f), isFlat = false, isVertical = false, isSeparating = true, isOccluding = false)
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891, Posture(isTabletop = true, hingeList = listOf(hinge))), systemBars = PhoneSystemBars)
        robot.tapMiniPlayer()
        shot("flip-tabletop-now-playing")
    }

    @Test
    @Config(qualifiers = "w700dp-h840dp-xhdpi")
    fun foldableUnfoldedMedium() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(700, 840))
        levels("foldable-unfolded-medium")
        libraryDetail("foldable-unfolded-medium")
    }

    @Test
    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    fun foldableUnfoldedExpanded() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(841, 701))
        levels("foldable-unfolded-expanded")
        libraryDetail("foldable-unfolded-expanded")
    }

    @Test
    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    fun foldableBookPosture() {
        // Half-opened like a book: a separating vertical hinge down the middle, in window pixels.
        val hinge = HingeInfo(bounds = Rect(1680f, 0f, 1684f, 1402f), isFlat = false, isVertical = true, isSeparating = true, isOccluding = false)
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(841, 701, Posture(isTabletop = false, hingeList = listOf(hinge))))
        levels("foldable-book")
        libraryDetail("foldable-book")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(1280, 800))
        levels("tablet")
        libraryDetail("tablet")
    }

    @Test
    @Config(qualifiers = "w1600dp-h1000dp-mdpi")
    fun desktopExtraLarge() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(1600, 1000))
        robot.tapMiniPlayer()
        libraryDetail("extra-large")
    }

    private companion object {
        val sampleQueue = sampleShellQueue()
        val sampleProgress = PlayerProgress(positionMs = 60_000, durationMs = sampleQueue.current!!.durationMs.toLong())

        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/shell")
        }
    }
}
