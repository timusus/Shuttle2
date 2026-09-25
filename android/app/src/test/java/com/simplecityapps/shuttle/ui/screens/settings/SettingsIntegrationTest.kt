package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
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
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val store = SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() })
    private val effects = FakeSettingsEffects()
    private val robot = SettingsRobot(composeTestRule)

    @Test
    fun `toggling a switch stores it and redraws it`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, SettingsViewModel(store, effects))
        robot.assertSwitchOff("Pure black")

        robot.tapText("Pure black")

        robot.assertSwitchOn("Pure black")
        store.preference(AppearanceSettings.PureBlack).value shouldBe true
    }

    @Test
    fun `picking a theme stores it and shows it`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, SettingsViewModel(store, effects))

        robot.tapText("Theme")
        robot.tapDialogText("Dark")

        robot.assertDisplayed("Dark")
        store.preference(AppearanceSettings.Theme).value shouldBe ThemeMode.Dark
        effects.changes shouldBe listOf(AppearanceSettings.Theme.key to ThemeMode.Dark)
    }

    @Test
    fun `a change made elsewhere shows up`() {
        robot.setDestinationContentWithViewModel(SettingsDestination.Appearance, SettingsViewModel(store, effects))

        store.preference(AppearanceSettings.PureBlack).value = true

        robot.assertSwitchOn("Pure black")
    }
}
