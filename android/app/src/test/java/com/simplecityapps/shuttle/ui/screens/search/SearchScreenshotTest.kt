package com.simplecityapps.shuttle.ui.screens.search

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
 * Records the Search screen at phone size into `docs/design/search/` for review (#377). A no-op under plain
 * `testDebugUnitTest`; record with `./gradlew :android:app:recordRoborazziDebug --tests '*SearchScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SearchScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = SearchRobot(composeTestRule)

    private fun shot(name: String, uiState: SearchUiState, theme: ThemeMode = ThemeMode.Light, query: String = "") {
        robot.queryState.edit { replace(0, length, query) }
        robot.setContent(uiState, theme)
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = RoborazziOptions(captureType = RoborazziOptions.CaptureType.Screenshot()),
        )
    }

    @Test
    fun start() = shot("start", SearchScenarios.start)

    @Test
    fun recent() = shot("recent", SearchScenarios.recent)

    @Test
    fun results() = shot("results", SearchScenarios.results, query = "radiohead")

    @Test
    fun resultsDark() = shot("results-dark", SearchScenarios.results, ThemeMode.Dark, query = "radiohead")

    @Test
    fun songsOnly() = shot("songs-only", SearchScenarios.songsOnly, query = "radiohead")

    @Test
    fun noResults() = shot("no-results", SearchScenarios.noResults, query = "zzzz")

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/search")
        }
    }
}
