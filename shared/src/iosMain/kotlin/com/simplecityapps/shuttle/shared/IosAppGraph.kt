package com.simplecityapps.shuttle.shared

import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.shared.artwork.ArtworkUrls
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.playback.IosPlayerController
import com.simplecityapps.shuttle.shared.sources.ServerSignIn
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsViewModel
import com.simplecityapps.shuttle.ui.screens.home.HomeViewModel
import com.simplecityapps.shuttle.ui.screens.library.GenreDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.settings.SettingsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoViewModel
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerViewModel
import com.simplecityapps.shuttle.ui.screens.sources.SourcesViewModel
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInViewModel
import com.simplecityapps.shuttle.ui.screens.tageditor.TagEditorViewModel
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
 * new instance per read, so Swift keeps each in `ViewModelCache`. A ViewModel with arguments comes from its assisted
 * factory's property instead (`albumDetailViewModelFactory.create(groupKey:)`), a new instance per `create`.
 * [metroViewModelFactory] is there too, but Swift can't build its `KClass` keys.
 *
 * Three shared ViewModels are excluded until iOS binds what they need (phase-4-viewmodels.md, "Wave 3" and "Wave 4"):
 * `SettingsViewModel` needs a `SettingsCatalog`, which waits on `LibrarySettings`/`PlaybackSettings`/
 * `DownloadSettings` reaching core, and a `SettingsEffects`; `EqualizerViewModel` needs an `EqualizerControl`,
 * `EqualizerFrequencyResponse` and `EqualizerPresetStore`, which come with iOS's `AVAudioUnitEQ` in phase 6;
 * `TagEditorViewModel` needs a `TagFileAccess`, which iOS gets with local files (security-scoped folders and a TagLib
 * wrapper) in phase 8. Until then nothing offers tag editing on iOS: only the local provider supports it.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [SettingsViewModel::class, EqualizerViewModel::class, TagEditorViewModel.Factory::class],
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

    /** Authenticated artwork urls for songs, albums and album artists; Swift's `ArtworkLoader` fetches and decodes. */
    val artworkUrls: ArtworkUrls

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

    val albumDetailViewModelFactory: AlbumDetailViewModel.Factory
    val albumArtistDetailViewModelFactory: AlbumArtistDetailViewModel.Factory
    val genreDetailViewModelFactory: GenreDetailViewModel.Factory
    val playlistDetailViewModelFactory: PlaylistDetailViewModel.Factory
    val smartPlaylistDetailViewModelFactory: SmartPlaylistDetailViewModel.Factory
    val songInfoViewModelFactory: SongInfoViewModel.Factory

    /** A Jellyfin or Emby server's sign-in form, including Jellyfin Quick Connect. Plex joins with its provider. */
    val serverSignInViewModelFactory: ServerSignInViewModel.Factory

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
