package com.simplecityapps.shuttle.ui.shell.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.bumptech.glide.SampleArtworkGlide
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.shell.AppShellRobot
import com.simplecityapps.shuttle.ui.shell.PhoneSystemBars
import com.simplecityapps.shuttle.ui.shell.sampleShellQueue
import com.simplecityapps.shuttle.ui.shell.windowInfo
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
 * Records Now Playing's extras on a phone into `docs/design/player-extras/` for review (#377, #400):
 * the artwork scheme under three contrasting covers and a dark one, the sleep timer and Playback & sound. A no-op
 * under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*PlayerExtrasScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PlayerExtrasScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = AppShellRobot(composeTestRule)

    @Before
    fun installSampleArtwork() = SampleArtworkGlide.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkGlide.uninstall()

    /** The whole screen, sheets included: they sit in windows of their own. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String) {
        composeTestRule.waitForIdle()
        captureScreenRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    private fun nowPlaying(playing: Int) {
        robot.setContent(queue = sampleShellQueue(size = 16, playing = playing), window = windowInfo(411, 891), systemBars = PhoneSystemBars)
        robot.tapMiniPlayer()
    }

    @Test
    fun artworkSchemes() {
        nowPlaying(ArtworkCovers.first())
        ArtworkCovers.forEachIndexed { index, playing ->
            robot.setQueue(sampleShellQueue(size = 16, playing = playing))
            shot("now-playing-artwork-${index + 1}")
        }
    }

    @Test
    fun darkArtworkScheme() {
        nowPlaying(DarkCover)
        shot("now-playing-artwork-dark")
    }

    @Test
    fun sleepTimer() {
        nowPlaying(ArtworkCovers[0])
        robot.tapPanelButton(NowPlayingPanel.SleepTimer)
        shot("sleep-timer-new")
        robot.tapText("Start timer")
        shot("sleep-timer-running")
    }

    @Test
    fun playbackAndSound() {
        nowPlaying(ArtworkCovers[1])
        robot.setQueue(sampleShellQueue(size = 16, playing = ArtworkCovers[1]).copy(playbackSpeed = 1.25f))
        shot("playback-speed-bar")
        robot.tapPanelButton(NowPlayingPanel.PlaybackSound)
        shot("playback-sound")
    }

    private companion object {
        /** Queue positions whose covers seed clearly different schemes: Blue Hours, Cassette Summer and Undertow. */
        val ArtworkCovers = listOf(6, 4, 14)

        /** Night Bus Frequencies: a near-black cover, whose seed the player lifts to a usable tone (#409). */
        const val DarkCover = 1

        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/player-extras")
        }
    }
}
