package com.simplecityapps.shuttle.ui.screens.sources

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyScreen
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationScreen
import com.simplecityapps.shuttle.ui.screens.settings.SettingsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
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
 * Records first run and Settings > Sources at phone size into `docs/design/onboarding/` for review (#379). A no-op
 * under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*OnboardingScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Skip the whole render (#538) unless Roborazzi is recording or verifying;
    // verifyRoborazziDebug still renders every board on every landing.
    @Before
    fun skipUnlessRoborazziActive() = assumeTrue(roborazziSystemPropertyTaskType().isEnabled())

    private fun shot(
        name: String,
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent { S2AppTheme(AppThemeState(theme = ThemeMode.Light), content) }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions
        )
    }

    /** Centred in the whole window, as the Library shows it under its top bar. */
    private fun emptyState(state: LibraryAvailability.Empty): @Composable () -> Unit = {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                LibraryEmptyScreen(state = state, onAllowAccess = {}, onOpenAppSettings = {}, onScan = {}, onConnectServer = {})
            }
        }
    }

    @Test
    fun noPermission() = shot("empty-no-permission", emptyState(LibraryAvailability.Empty(MusicAccess.NotRequested)))

    @Test
    fun denied() = shot("empty-denied", emptyState(LibraryAvailability.Empty(MusicAccess.PermanentlyDenied)))

    @Test
    fun scanning() = shot("empty-scanning", emptyState(LibraryAvailability.Empty(MusicAccess.Granted, ScanProgress("Juniper Static • Chlorophyll Loop", 0.4f))))

    @Test
    fun sources() = shot("sources") {
        SettingsDestinationScreen(
            screen = SettingsCatalog.screen(SettingsDestination.Sources),
            uiState = SettingsUiState(),
            onNavigateUp = {},
            onSwitchChange = { _, _ -> },
            onChoiceSelect = { _, _ -> },
            onSliderChange = { _, _ -> },
            onAction = {},
            onOpenLink = {},
            leadingContent = { sourcesContent(SourcesScenarios.configured, SourcesScenarios.noActions) },
        )
    }

    companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/onboarding")
        }
    }
}
