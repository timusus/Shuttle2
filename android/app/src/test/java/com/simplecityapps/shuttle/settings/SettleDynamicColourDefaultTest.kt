package com.simplecityapps.shuttle.settings

import com.simplecityapps.shuttle.ui.theme.AppThemeViewModel
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettleDynamicColourDefaultTest {
    private val store = SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() })
    private val settings = AppearanceSettings(store)

    private fun themeOnLaunch() = SettleDynamicColourDefault(settings)().let { AppThemeViewModel(ObserveSetting(store), ReadSetting(store)).uiState.value }

    @Test
    fun `a new install gets dynamic colour`() {
        themeOnLaunch().dynamicColour shouldBe true
    }

    @Test
    fun `a user who picked an accent keeps it`() {
        settings.accent.value = Accent.Orange

        themeOnLaunch().dynamicColour shouldBe false
    }

    @Test
    fun `a user who kept the default accent explicitly keeps it`() {
        settings.accent.value = Accent.Default

        themeOnLaunch().dynamicColour shouldBe false
    }

    @Test
    fun `a saved choice wins`() {
        settings.dynamicColour.value = false
        themeOnLaunch().dynamicColour shouldBe false

        settings.dynamicColour.value = true
        settings.accent.value = Accent.Green
        themeOnLaunch().dynamicColour shouldBe true
    }

    @Test
    fun `picking an accent after launch leaves dynamic colour on`() {
        themeOnLaunch()
        settings.accent.value = Accent.Purple

        themeOnLaunch().dynamicColour shouldBe true
    }
}
