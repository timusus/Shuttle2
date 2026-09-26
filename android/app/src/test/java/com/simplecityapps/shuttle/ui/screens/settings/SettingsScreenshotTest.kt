package com.simplecityapps.shuttle.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.DocsDesignRoborazziOptions
import com.simplecityapps.shuttle.ui.preview.sampleSongs
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerBandState
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerScreen
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerUiState
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsScreen
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsUiState
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.theme.AppThemeState
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Records the settings screens at phone size into `docs/design/settings/` for review (#378). A no-op
 * under plain `testDebugUnitTest`; record with
 * `./gradlew :android:app:recordRoborazziDebug --tests '*SettingsScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(
        name: String,
        theme: AppThemeState = AppThemeState(theme = ThemeMode.Light),
        content: @Composable () -> Unit
    ) {
        composeTestRule.setContent { S2AppTheme(theme, content) }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = File(shotsDir, "$name.png").path,
            roborazziOptions = DocsDesignRoborazziOptions
        )
    }

    private fun destination(
        destination: SettingsDestination,
        uiState: SettingsUiState = SettingsUiState()
    ): @Composable () -> Unit = {
        SettingsDestinationScreen(
            screen = SettingsCatalog.screen(destination),
            uiState = uiState,
            onNavigateUp = {},
            onSwitchChange = { _, _ -> },
            onChoiceSelect = { _, _ -> },
            onSliderChange = { _, _ -> },
            onAction = {},
            onOpenLink = {},
            versionName = "2026.09.25"
        )
    }

    @Test
    fun root() = shot("root") { SettingsRootScreen(onNavigateUp = {}, onOpenDestination = {}, onOpenPro = {}) }

    @Test
    fun appearance() = shot("appearance", content = destination(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack))

    @Test
    fun appearanceDark() = shot("appearance-dark", AppThemeState(theme = ThemeMode.Dark), destination(SettingsDestination.Appearance))

    @Test
    fun playbackAndSound() = shot("playback-and-sound", content = destination(SettingsDestination.PlaybackAndSound))

    @Test
    fun sources() = shot("sources", content = destination(SettingsDestination.Sources))

    @Test
    fun library() = shot("library", content = destination(SettingsDestination.Library, SettingsScenarios.scannedWeekly))

    @Test
    fun privacy() = shot("privacy", content = destination(SettingsDestination.Privacy))

    @Test
    fun about() = shot("about", content = destination(SettingsDestination.About))

    @Test
    fun equalizer() = shot("equalizer") {
        val preset = Equalizer.Presets.bassBoost
        EqualizerScreen(
            uiState = EqualizerUiState(enabled = true, selectedPreset = preset, bands = preset.bands.map { EqualizerBandState(it.centerFrequency, it.gain.toFloat()) }),
            onNavigateUp = {},
            onEnabledChange = {},
            onPresetSelect = {},
            onBandGainChange = { _, _ -> },
            onBandGainChangeFinished = {}
        )
    }

    @Test
    fun excludedSongs() = shot("excluded-songs") {
        ExcludedSongsScreen(
            uiState = ExcludedSongsUiState(
                songs = sampleSongs(2).map { it.copy(blacklisted = true) },
                loading = false
            ),
            onNavigateUp = {},
            onInclude = {},
            onIncludeAll = {}
        )
    }

    private companion object {
        val shotsDir: File by lazy {
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .first { File(it, "gradlew").exists() && File(it, "docs/design").isDirectory }
                .resolve("docs/design/settings")
        }
    }
}
