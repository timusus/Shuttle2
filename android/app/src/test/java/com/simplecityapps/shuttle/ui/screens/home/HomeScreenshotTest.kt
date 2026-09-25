package com.simplecityapps.shuttle.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.settings.ThemeMode
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the Home screen at phone size into `docs/design/home/` for review (#377). A no-op under plain
 * `testDebugUnitTest`; record with `./gradlew :android:app:recordRoborazziDebug --tests '*HomeScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = HomeRobot(composeTestRule)

    private fun shot(name: String, uiState: HomeUiState, theme: ThemeMode = ThemeMode.Light) {
        robot.setContent(uiState, theme)
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    @Test
    fun content() = shot("content", HomeScenarios.content)

    @Test
    fun contentDark() = shot("content-dark", HomeScenarios.content, ThemeMode.Dark)

    @Test
    fun whatsNew() = shot("whats-new", HomeScenarios.whatsNew)

    @Test
    fun unplayed() = shot("unplayed", HomeScenarios.unplayed)

    @Test
    fun empty() = shot("empty", HomeScenarios.empty)

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/home")
        }
    }
}
