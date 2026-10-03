package com.simplecityapps.shuttle.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.shell.player.NowPlayingPanel
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
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
 * sample-library queue with its generated covers ([SampleArtworkCoil]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    // Skip the whole render (#538) unless Roborazzi is recording or verifying;
    // verifyRoborazziDebug still renders every board on every landing.
    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    @Before
    fun installSampleArtwork() = SampleArtworkCoil.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkCoil.uninstall()

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
        robot.tapPanelButton(NowPlayingPanel.Queue)
        shot("$prefix-queue")
        robot.tapPanelButton(NowPlayingPanel.Queue)
        robot.pressBack()
    }

    /** A compact sheet: Mini, the full player, and each panel open. */
    private fun sheetLevels(prefix: String) {
        shot("$prefix-mini")
        robot.tapMiniPlayer()
        shot("$prefix-now-playing")
        NowPlayingPanel.entries.forEach { panel ->
            robot.tapPanelButton(panel)
            shot("$prefix-${panel.shotName}")
            robot.tapPanelButton(panel)
        }
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

    /**
     * A settings page over the shell: no nav bar or rail, the mini player (or pane) still there (#791). With the pane
     * open the page comes from its Playback & sound panel, and with no Settings list beneath it fills the content area.
     */
    private fun settings(
        prefix: String,
        paneOpen: Boolean = false,
    ) {
        if (paneOpen) {
            robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
            robot.scrollToAndTapText("More sound settings")
        } else {
            robot.openSoundSettings()
        }
        shot("$prefix-settings")
    }

    /** The Settings list with its first page beside it, list-detail from Expanded (#770). */
    private fun settingsListDetail(prefix: String) {
        robot.openSettings()
        shot("$prefix-settings-list-detail")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSettings() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        settings("phone")
    }

    /** A phone on its side: the rail gone, the page clears the cutout, and the mini player's fill runs under the gesture bar. */
    @Test
    @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun phoneLandscapeSettings() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(891, 411), systemBars = PhoneSystemBars.copy(leftCutoutDp = 32))
        settings("phone-landscape")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun phoneSongInfoSheet() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        robot.openSongInfo()
        shot("phone-song-info-sheet")
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

    /** Now Playing and the mini player at 200% text, where fixed-height chrome used to clip (#730). */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun phoneLargeText() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        shot("phone-large-text-mini")
        robot.tapMiniPlayer()
        shot("phone-large-text-now-playing")
        robot.tapPanelButton(NowPlayingPanel.Queue)
        shot("phone-large-text-queue")
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
        settings("foldable-unfolded-medium")
    }

    @Test
    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    fun foldableUnfoldedExpanded() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(841, 701))
        levels("foldable-unfolded-expanded")
        libraryDetail("foldable-unfolded-expanded")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() {
        robot.setContent(queue = sampleQueue, progress = sampleProgress, window = windowInfo(1280, 800))
        levels("tablet")
        libraryDetail("tablet")
        // levels() leaves the pane open.
        settings("tablet", paneOpen = true)
        // Back closes the pane's panel, then the page.
        robot.pressBack()
        robot.pressBack()
        settingsListDetail("tablet")
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
