package com.simplecityapps.shuttle.shared

import androidx.lifecycle.viewmodel.CreationExtras
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import dev.zacsweers.metro.createGraphFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

/** Metro on Kotlin/Native sees :android:presentation's `AppScope` contributions from its klib. */
class SharedAppGraphTest {
    private val keyValueStore = InMemoryKeyValueStore()
    private val graph = createGraphFactory<SharedAppGraph.Factory>().create(keyValueStore, bundledText = { null }, appVersion = { "1.0.0" })

    @Test
    fun typedPropertyBuildsTheViewModelOverTheGivenStore() {
        SaveSetting(SettingsStore(keyValueStore))(AppearanceSettings.ShowHomeOnLaunch, true)

        graph.shellViewModel.uiState.value.startTab shouldBe ShellTab.Home
    }

    @Test
    fun contributedFactoryCreatesTheContributedViewModels() {
        val factory = graph.metroViewModelFactory

        factory.create(ShellViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<ShellViewModel>()
        factory.create(LibraryViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<LibraryViewModel>()
        factory.create(LicencesViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<LicencesViewModel>()
        factory.create(WhatsNewViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<WhatsNewViewModel>()
    }
}
