package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the tag editor at phone size into `docs/design/tageditor/` for review (#377). A no-op under plain
 * `testDebugUnitTest`; record with `./gradlew :android:app:recordRoborazziDebug --tests '*TagEditorScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class TagEditorScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Skip the whole render (#538) unless Roborazzi is recording or verifying;
    // verifyRoborazziDebug still renders every board on every landing.
    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    private fun shot(name: String, uiState: TagEditorUiState, theme: ThemeMode = ThemeMode.Light) {
        composeTestRule.setContent {
            S2AppTheme(AppThemeState(theme = theme)) {
                TagEditorScreen(uiState = uiState, onNavigateUp = {}, onFieldChange = { _, _ -> }, onFieldReset = {}, onSave = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    @Test
    fun single() = shot("single", singleSongEditing())

    @Test
    fun singleDark() = shot("single-dark", singleSongEditing(), ThemeMode.Dark)

    @Test
    fun edited() = shot("edited", writingTags().copy(writing = null))

    @Test
    fun batch() = shot("batch", batchEditing())

    @Test
    fun batchDark() = shot("batch-dark", batchEditing(), ThemeMode.Dark)

    @Test
    fun reading() = shot("reading", readingTags)

    @Test
    fun writing() = shot("writing", writingTags())

    @Test
    fun unreadable() = shot("unreadable", TagEditorUiState.Unreadable)

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/tageditor")
        }
    }
}
