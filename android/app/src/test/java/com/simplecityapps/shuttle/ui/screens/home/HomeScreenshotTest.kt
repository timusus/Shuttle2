package com.simplecityapps.shuttle.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.SampleArtworkCoil
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
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
 * Records the Home screen at phone size into `docs/design/home/` for review (#377). A no-op under plain
 * `testDebugUnitTest`; record with `./gradlew :android:app:recordRoborazziDebug --tests '*HomeScreenshotTest*'`. The
 * shelves show the sample library with its generated covers ([SampleArtworkCoil]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class HomeScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val robot = HomeRobot(composeTestRule)

    @Before
    fun installSampleArtwork() = SampleArtworkCoil.install(ApplicationProvider.getApplicationContext())

    @After
    fun uninstallSampleArtwork() = SampleArtworkCoil.uninstall()

    private fun shot(name: String, uiState: HomeUiState, theme: ThemeMode = ThemeMode.Light) {
        robot.setContent(uiState, theme)
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
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

    @Test
    fun emptyNoPermission() {
        robot.setContent(HomeScenarios.empty, emptyContent = emptyContentFor(MusicAccess.NotRequested))
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "empty-no-permission.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    @Test
    fun emptyDenied() {
        robot.setContent(HomeScenarios.empty, emptyContent = emptyContentFor(MusicAccess.PermanentlyDenied))
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "empty-denied.png").path,
            roborazziOptions = DocsDesignRoborazziOptions,
        )
    }

    /** [LibraryEmptyScreen] at the given access state, as Home wires it into its empty-state slot (#422). */
    private fun emptyContentFor(access: MusicAccess): @Composable (Modifier) -> Unit = { modifier ->
        LibraryEmptyScreen(
            state = LibraryAvailability.Empty(access),
            onAllowAccess = {},
            onOpenAppSettings = {},
            onScan = {},
            onConnectServer = {},
            modifier = modifier,
        )
    }

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/home")
        }
    }
}
