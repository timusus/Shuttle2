package com.simplecityapps.shuttle.shared

import androidx.lifecycle.viewmodel.CreationExtras
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.UserDefaultsKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.shared.local.IosLocalFiles
import com.simplecityapps.shuttle.shared.playback.FakeIosAudioPlayer
import com.simplecityapps.shuttle.shared.settings.IosSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.library.GenreDetailCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.GenreDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistDetailCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoViewModel
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInViewModel
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
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
    private val player = FakeIosAudioPlayer()

    private val graph = createIosAppGraph(player, IosLocalFiles.None)

    @AfterTest
    fun removeTheSettingsWritten() {
        UserDefaultsKeyValueStore().edit {
            remove(AppearanceSettings.ShowHomeOnLaunch.key)
            remove(EqualizerSettings.Enabled.key)
        }
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
        graph.genreCoversViewModel
        graph.playlistListViewModel
        graph.mediaActionsViewModel
        graph.excludedSongsViewModel
        graph.licencesViewModel
        graph.whatsNewViewModel
        graph.sourcesViewModel
        graph.sourceSetupViewModel
        graph.settingsViewModel
        graph.equalizerViewModel
        graph.albumDetailViewModelFactory
        graph.albumArtistDetailViewModelFactory
        graph.genreDetailViewModelFactory
        graph.playlistDetailViewModelFactory
        graph.genreDetailCoversViewModelFactory
        graph.playlistDetailCoversViewModelFactory
        graph.smartPlaylistDetailViewModelFactory
        graph.songInfoViewModelFactory
        graph.serverSignInViewModelFactory
        graph.playerViewModelFactory
        graph.mediaSources
        graph.songImportStateProvider
        graph.artworkUrls
        graph.recordPlays
        graph.recordResumePoints
    }

    @Test
    fun theAssistedFactoriesCreateTheirViewModels() {
        graph.genreDetailViewModelFactory.create("Jazz").shouldBeInstanceOf<GenreDetailViewModel>()
        graph.playlistDetailViewModelFactory.create(1L).shouldBeInstanceOf<PlaylistDetailViewModel>()
        graph.genreDetailCoversViewModelFactory.create("Jazz").shouldBeInstanceOf<GenreDetailCoversViewModel>()
        graph.playlistDetailCoversViewModelFactory.create(1L).shouldBeInstanceOf<PlaylistDetailCoversViewModel>()
        graph.songInfoViewModelFactory.create(1L).shouldBeInstanceOf<SongInfoViewModel>()
        graph.serverSignInViewModelFactory.create(MediaProviderType.Jellyfin).shouldBeInstanceOf<ServerSignInViewModel>()
        graph.serverSignInViewModelFactory.create(MediaProviderType.Emby).shouldBeInstanceOf<ServerSignInViewModel>()
    }

    @Test
    fun theViewModelsSettingsAreTheAppsUserDefaults() {
        SaveSetting(SettingsStore(UserDefaultsKeyValueStore()))(AppearanceSettings.ShowHomeOnLaunch, false)

        graph.shellViewModel.uiState.value.startTab shouldBe ShellTab.Library
    }

    @Test
    fun iosOpensOnHomeUntilTheUserSaysOtherwise() {
        graph.shellViewModel.uiState.value.startTab shouldBe ShellTab.Home
    }

    @Test
    fun settingsReadTheIosCatalog() {
        graph.settingsCatalog shouldBeSameInstanceAs IosSettingsCatalog
    }

    @Test
    fun thePlayerViewModelIsBuiltOverTheGraphsPlayback() {
        val viewModel = graph.createPlayerViewModel()

        viewModel.shouldBeInstanceOf<PlayerViewModel>()
        // No saved queue on iOS yet: nothing stands in for one, so the player starts empty.
        viewModel.uiState.value.player.hasQueue shouldBe false
        viewModel.uiState.value.player.castAvailable shouldBe false
    }

    @Test
    fun onePlayerControllerIsTheGraphsPlayback() {
        graph.playerController shouldBeSameInstanceAs graph.playerController
    }

    @Test
    fun theSavedEqualizerReachesTheEngineWithThePlayer() {
        graph.playerController

        player.equalizers.size shouldBe 1
        player.equalizers.last().coefficients.size shouldBe 5 * Equalizer.Presets.flat.bands.size
    }

    @Test
    fun theEqualizerScreenDrivesTheEngine() {
        graph.playerController
        graph.equalizerViewModel.onEnabledChange(true)

        player.equalizers.last().enabled shouldBe true
    }

    @Test
    fun contributedFactoryCreatesTheContributedViewModels() {
        val factory = graph.metroViewModelFactory

        factory.create(ShellViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<ShellViewModel>()
        factory.create(LibraryViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<LibraryViewModel>()
        factory.create(SongListViewModel::class, CreationExtras.Empty).shouldBeInstanceOf<SongListViewModel>()
    }
}
