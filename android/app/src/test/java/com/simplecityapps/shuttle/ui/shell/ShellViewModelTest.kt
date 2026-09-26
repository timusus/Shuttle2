package com.simplecityapps.shuttle.ui.shell

import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShellViewModelTest {
    private val store = SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() })
    private val settings = AppearanceSettings(store)

    @Test
    fun `opens on Library by default, as 1_0_10 did`() {
        ShellViewModel(ReadSetting(store)).uiState.value.startTab shouldBe ShellTab.Library
    }

    @Test
    fun `opens on Home when Show Home on launch is on`() {
        settings.showHomeOnLaunch.value = true

        ShellViewModel(ReadSetting(store)).uiState.value.startTab shouldBe ShellTab.Home
    }

    @Test
    fun `a change after launch waits for the next launch`() {
        val viewModel = ShellViewModel(ReadSetting(store))

        settings.showHomeOnLaunch.value = true

        viewModel.uiState.value.startTab shouldBe ShellTab.Library
    }
}
