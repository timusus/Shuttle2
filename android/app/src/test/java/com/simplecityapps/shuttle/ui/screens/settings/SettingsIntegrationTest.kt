package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.shuttle.entitlement.TryUseProFeature
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SharedPreferencesKeyValueStore
import com.simplecityapps.shuttle.scrobbling.IsLastFmConfigured
import com.simplecityapps.shuttle.settings.Accent
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.shuttle.ui.screens.settings.backup.FakeLibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The full chain: stored preference -> real [SettingsViewModel] -> screen -> tap -> stored preference. */
@RunWith(RobolectricTestRunner::class)
class SettingsIntegrationTest {
    // Ordered explicitly (JUnit doesn't guarantee declaration order for unordered @Rules):
    // Dispatchers.Main must already be the test dispatcher before composeTestRule builds its
    // Compose test environment, and must stay set until that environment (and its recomposer
    // coroutine, which dispatches onto Main) has fully torn down -- otherwise resetMain() can
    // race a still-live recomposer dispatch and throw "Dispatchers.Main is used concurrently
    // with setting it" (#437).
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private val store = SettingsStore(SharedPreferencesKeyValueStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }))
    private val effects = FakeSettingsEffects()
    private val backupFlow = FakeLibraryBackupFlow()
    private val robot = SettingsRobot(composeTestRule)

    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())

    private fun viewModel(): SettingsViewModel {
        val presetStore = KeyValueEqualizerPresetStore(InMemoryKeyValueStore())
        return SettingsViewModel(
            ObserveSetting(store),
            ReadSetting(store),
            SaveSetting(store),
            ReadLastScanDate(preferences),
            ObserveLastScanDate(preferences),
            ObserveEqualizerPreset(presetStore),
            ReadEqualizerPreset(presetStore),
            IsLastFmConfigured { false },
            effects,
            AndroidSettingsCatalog,
            backupFlow,
            TryUseProFeature { true }
        )
    }

    @Test
    fun `toggling a switch stores it and redraws it`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, viewModel())
        robot.assertSwitchOff("Pure black")

        robot.tapText("Pure black")

        robot.assertSwitchOn("Pure black")
        store.preference(AppearanceSettings.PureBlack).value shouldBe true
    }

    @Test
    fun `picking a theme stores it and shows it`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, viewModel())

        robot.tapText("Theme")
        robot.tapDialogText("Dark")

        robot.assertDisplayed("Dark")
        store.preference(AppearanceSettings.Theme).value shouldBe ThemeMode.Dark
        effects.changes shouldBe listOf(AppearanceSettings.Theme.key to ThemeMode.Dark)
    }

    @Test
    fun `turning dynamic colour off frees the accent to be picked`() {
        store.preference(AppearanceSettings.DynamicColour).value = true
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, viewModel())
        robot.assertNotEnabled("Accent")

        robot.tapText("Dynamic colour")
        robot.tapText("Accent")
        robot.tapDialogText("Green")

        store.preference(AppearanceSettings.DynamicColour).value shouldBe false
        store.preference(AppearanceSettings.AccentColour).value shouldBe Accent.Green
    }

    @Test
    fun `a change made elsewhere shows up`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, viewModel())

        store.preference(AppearanceSettings.PureBlack).value = true

        robot.assertSwitchOn("Pure black")
    }
}
