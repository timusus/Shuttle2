package com.simplecityapps.shuttle.shared

import androidx.lifecycle.viewmodel.CreationExtras
import com.simplecityapps.shuttle.persistence.UserDefaultsKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.shared.playback.FakeIosAudioPlayer
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The whole iOS graph builds on the simulator, and every ViewModel Swift reaches resolves: Metro on Kotlin/Native
 * merges the shared modules' `AppScope` contributions from their klibs, and :shared's iOS containers bind the rest.
 */
class IosAppGraphTest {
    private val graph = createIosAppGraph(FakeIosAudioPlayer())

    @AfterTest
    fun removeTheSettingWritten() {
        UserDefaultsKeyValueStore().edit { remove(AppearanceSettings.ShowHomeOnLaunch.key) }
    }

    @Test
    fun everyTypedPropertyResolves() {
        graph.shellViewModel
        graph.homeViewModel
        graph.libraryViewModel
        graph.libraryEmptyViewModel
        graph.songListViewModel
        graph.albumListViewModel
        graph.albumArtistListViewModel
        graph.genreListViewModel
        graph.playlistListViewModel
        graph.mediaActionsViewModel
        graph.excludedSongsViewModel
        graph.licencesViewModel
        graph.whatsNewViewModel
        graph.sourcesViewModel
        graph.serverTypePickerViewModel
        graph.mediaSources
        graph.songImportStateProvider
        graph.serverSignIn
    }

    @Test
    fun theViewModelsSettingsAreTheAppsUserDefaults() {
        SaveSetting(SettingsStore(UserDefaultsKeyValueStore()))(AppearanceSettings.ShowHomeOnLaunch, true)

        graph.shellViewModel.uiState.value.startTab shouldBe ShellTab.Home
    }

    @Test
    fun onePlayerControllerIsTheGraphsPlayback() {
        graph.playerController shouldBeSameInstanceAs graph.playerController
    }

    @Test
    fun contributedFactoryCreatesTheContributedViewModels() {
        val factory = graph.metroViewModelFactory

        factory.create(ShellViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<ShellViewModel>()
        factory.create(LibraryViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<LibraryViewModel>()
        factory.create(SongListViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<SongListViewModel>()
    }
}
