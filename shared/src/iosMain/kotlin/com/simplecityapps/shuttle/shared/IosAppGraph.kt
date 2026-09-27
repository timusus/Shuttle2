package com.simplecityapps.shuttle.shared

import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.playback.IosPlayerController
import com.simplecityapps.shuttle.shared.sources.ServerSignIn
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsViewModel
import com.simplecityapps.shuttle.ui.screens.home.HomeViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.settings.SettingsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerViewModel
import com.simplecityapps.shuttle.ui.screens.sources.SourcesViewModel
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The iOS app's dependency graph, the twin of Android's `AppGraph` (docs/architecture/ios-port/phase-5-ios-app.md):
 * every `AppScope` contribution the shared modules make, merged from their klibs, plus :shared's iOS containers
 * (`di/`: persistence, networking, playback, the platform seams). Swift builds it once at launch with
 * [createIosAppGraph], handing over what only Swift can make, and reaches a ViewModel through its typed property: a
 * new instance per read, so Swift keeps each in `ViewModelCache`. [metroViewModelFactory] is there too, but Swift
 * can't build its `KClass` keys.
 *
 * Two shared ViewModels are excluded until iOS binds what they need (phase-4-viewmodels.md, "Wave 3"):
 * `SettingsViewModel` needs a `SettingsCatalog`, which waits on `LibrarySettings`/`PlaybackSettings`/
 * `DownloadSettings` reaching core, and a `SettingsEffects`; `EqualizerViewModel` needs an `EqualizerControl`,
 * `EqualizerFrequencyResponse` and `EqualizerPresetStore`, which come with iOS's `AVAudioUnitEQ` in phase 6.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [SettingsViewModel::class, EqualizerViewModel::class],
)
interface IosAppGraph : ViewModelGraph {
    /** Playback: `PlaybackOperations`, and the queue through its `queueOperations`. One for the graph, on main. */
    val playerController: IosPlayerController

    /** The library's providers and the import that fills it: the Library's pull-to-refresh and launch import. */
    val mediaSources: MediaSources

    /** The running import's progress, for the Library root. */
    val songImportStateProvider: SongImportStateProvider

    /** Signs in to a server; the DEBUG launch seed's path until the phase 7 sign-in screen. */
    val serverSignIn: ServerSignIn

    val shellViewModel: ShellViewModel
    val homeViewModel: HomeViewModel
    val libraryViewModel: LibraryViewModel
    val libraryEmptyViewModel: LibraryEmptyViewModel
    val songListViewModel: SongListViewModel
    val albumListViewModel: AlbumListViewModel
    val albumArtistListViewModel: AlbumArtistListViewModel
    val genreListViewModel: GenreListViewModel
    val playlistListViewModel: PlaylistListViewModel
    val mediaActionsViewModel: MediaActionsViewModel
    val excludedSongsViewModel: ExcludedSongsViewModel
    val licencesViewModel: LicencesViewModel
    val whatsNewViewModel: WhatsNewViewModel
    val sourcesViewModel: SourcesViewModel
    val serverTypePickerViewModel: ServerTypePickerViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        /** [audioPlayer]: the Swift adapter over the S2Playback engine. */
        fun create(
            @Provides audioPlayer: IosAudioPlayer
        ): IosAppGraph
    }
}

/** Builds the graph: `IosAppGraphKt.createIosAppGraph(audioPlayer:)` from Swift, once per process. */
fun createIosAppGraph(audioPlayer: IosAudioPlayer): IosAppGraph = createGraphFactory<IosAppGraph.Factory>().create(audioPlayer)
