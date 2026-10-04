package com.simplecityapps.shuttle.shared

import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.localmediaprovider.local.favourites.FavouriteSender
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.ObservePaywallRequests
import com.simplecityapps.shuttle.playback.RecordPlays
import com.simplecityapps.shuttle.playback.RecordResumePoints
import com.simplecityapps.shuttle.playbackreporting.PlaybackReporting
import com.simplecityapps.shuttle.shared.artwork.ArtworkUrls
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import com.simplecityapps.shuttle.shared.local.IosLocalFiles
import com.simplecityapps.shuttle.shared.platform.BackgroundSync
import com.simplecityapps.shuttle.shared.playback.IosAudioPlayer
import com.simplecityapps.shuttle.shared.playback.IosPlayerController
import com.simplecityapps.shuttle.shared.telemetry.IosTelemetry
import com.simplecityapps.shuttle.shared.telemetry.IosTelemetryStartup
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsViewModel
import com.simplecityapps.shuttle.ui.screens.home.HomeViewModel
import com.simplecityapps.shuttle.ui.screens.library.GenreDetailCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.GenreDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistDetailCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.PlaylistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailViewModel
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.onboarding.SourceSetupViewModel
import com.simplecityapps.shuttle.ui.screens.search.LibrarySearchIndex
import com.simplecityapps.shuttle.ui.screens.search.SearchViewModel
import com.simplecityapps.shuttle.ui.screens.settings.SettingsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoViewModel
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.SourcesViewModel
import com.simplecityapps.shuttle.ui.screens.sources.servers.ReadServerLogin
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInViewModel
import com.simplecityapps.shuttle.ui.screens.tageditor.TagEditorViewModel
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel
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
 * `SettingsViewModel` reads `IosSettingsCatalog` (only the rows iOS acts on) through `IosSettingsEffects`.
 *
 * `EqualizerViewModel` drives `IosEqualizer`, which designs the S2Playback engine's filters (phase-6-playback.md).
 *
 * One shared ViewModel is excluded until iOS binds what it needs (phase-4-viewmodels.md, "Wave 4"):
 * `TagEditorViewModel` needs a `TagFileAccess`, a tag writer iOS doesn't have: it reads local files' tags with FFmpeg,
 * which doesn't write them in place. Until then nothing offers tag editing on iOS.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [TagEditorViewModel.Factory::class],
)
interface IosAppGraph : ViewModelGraph {
    /** Playback: `PlaybackOperations`, and the queue through its `queueOperations`. One for the graph, on main. */
    val playerController: IosPlayerController

    /** Records each song's plays and pause positions from [playerController]'s events; Swift starts it once, at launch. */
    val recordPlays: RecordPlays

    /** Keeps where each play context was left (#670) from [playerController]'s events; Swift starts it once, at launch. */
    val recordResumePoints: RecordResumePoints

    /** Sends the favourites made on Jellyfin, Emby and Plex songs to their servers (#497); Swift starts it once, at launch. */
    val favouriteSender: FavouriteSender

    /** The search index; Swift warms it once, at launch, so the first search doesn't wait for the build. */
    val librarySearchIndex: LibrarySearchIndex

    /** The library's providers and the import that fills it: the Library's pull-to-refresh and launch import. */
    val mediaSources: MediaSources

    /** The daily background sync, which Swift's `BGAppRefreshTask` runs. */
    val backgroundSync: BackgroundSync

    /** The running import's progress, for the Library root. */
    val songImportStateProvider: SongImportStateProvider

    /** Authenticated artwork urls for songs, albums and album artists; Swift's `ArtworkLoader` fetches and decodes. */
    val artworkUrls: ArtworkUrls

    /** Reports plays to Jellyfin, Emby and Plex (and replays missed ones); Swift starts it at launch. */
    val playbackReporting: PlaybackReporting

    /** The user's entitlement, fed from StoreKit by Swift's `StoreKitManager`. */
    val storeEntitlements: StoreEntitlements

    /** A gated action's paywall requests, which Swift's `PaywallPresenter` answers. */
    val observePaywallRequests: ObservePaywallRequests

    /** Crash reporting, analytics and their consent; Swift starts it first of all, at launch. */
    val telemetryStartup: IosTelemetryStartup

    /** The paywall and purchase events Swift's `PaywallPresenter` and `StoreKitManager` record. */
    val monetisationAnalytics: MonetisationAnalytics

    val shellViewModel: ShellViewModel
    val homeViewModel: HomeViewModel
    val libraryViewModel: LibraryViewModel
    val libraryEmptyViewModel: LibraryEmptyViewModel
    val songListViewModel: SongListViewModel
    val albumListViewModel: AlbumListViewModel
    val albumArtistListViewModel: AlbumArtistListViewModel
    val genreListViewModel: GenreListViewModel

    /** The genre rows' mosaic covers, apart from `genreListViewModel` so Android's list never queries them. */
    val genreCoversViewModel: GenreCoversViewModel
    val playlistListViewModel: PlaylistListViewModel
    val mediaActionsViewModel: MediaActionsViewModel
    val excludedSongsViewModel: ExcludedSongsViewModel
    val licencesViewModel: LicencesViewModel
    val whatsNewViewModel: WhatsNewViewModel
    val sourcesViewModel: SourcesViewModel
    val sourceSetupViewModel: SourceSetupViewModel
    val settingsViewModel: SettingsViewModel
    val equalizerViewModel: EqualizerViewModel
    val searchViewModel: SearchViewModel

    /** The rows `settingsViewModel` stores, for Swift's `SettingsView` to lay out. */
    val settingsCatalog: SettingsCatalog

    val albumDetailViewModelFactory: AlbumDetailViewModel.Factory
    val albumArtistDetailViewModelFactory: AlbumArtistDetailViewModel.Factory
    val genreDetailViewModelFactory: GenreDetailViewModel.Factory
    val playlistDetailViewModelFactory: PlaylistDetailViewModel.Factory
    val genreDetailCoversViewModelFactory: GenreDetailCoversViewModel.Factory
    val playlistDetailCoversViewModelFactory: PlaylistDetailCoversViewModel.Factory
    val smartPlaylistDetailViewModelFactory: SmartPlaylistDetailViewModel.Factory
    val songInfoViewModelFactory: SongInfoViewModel.Factory
    val folderListViewModelFactory: FolderListViewModel.Factory

    /** A Jellyfin, Emby or Plex server's sign-in form, including Jellyfin Quick Connect and Plex's two-factor code. */
    val serverSignInViewModelFactory: ServerSignInViewModel.Factory

    /** A signed-in Jellyfin, Emby or Plex server's saved address and user, for its row and detail in Sources. */
    val readServerLogin: ReadServerLogin

    /**
     * The mini player and Now Playing's ViewModel. Swift builds one through [createPlayerViewModel], which hands it the
     * saved state it needs.
     */
    val playerViewModelFactory: PlayerViewModel.Factory

    @DependencyGraph.Factory
    fun interface Factory {
        /**
         * [audioPlayer]: the Swift adapter over the S2Playback engine; [storage]: where preferences and the library live;
         * [localFiles]: this device's music files, Swift's `LocalLibrary`; [telemetry]: Sentry and PostHog.
         */
        fun create(
            @Provides audioPlayer: IosAudioPlayer,
            @Provides storage: IosStorage,
            @Provides localFiles: IosLocalFiles,
            @Provides telemetry: IosTelemetry
        ): IosAppGraph
    }
}

/** Builds the graph: `IosAppGraphKt.createIosAppGraph(audioPlayer:localFiles:telemetry:)` from Swift, once per process. */
fun createIosAppGraph(
    audioPlayer: IosAudioPlayer,
    localFiles: IosLocalFiles,
    telemetry: IosTelemetry
): IosAppGraph = createGraphFactory<IosAppGraph.Factory>().create(audioPlayer, IosStorage(null), localFiles, telemetry)

/**
 * A graph with storage of its own, for tests: preferences in the NSUserDefaults suite [isolatedStorage] rather than the
 * standard defaults, and an empty in-memory library rather than the app's database. Tests build several graphs at
 * once, which would otherwise restore each other's saved queue and modes, and query whatever library the simulator's
 * app holds. [localFiles] is none unless a test hands it some; telemetry is always none.
 */
fun createIosAppGraph(
    audioPlayer: IosAudioPlayer,
    isolatedStorage: String,
    localFiles: IosLocalFiles = IosLocalFiles.None
): IosAppGraph = createGraphFactory<IosAppGraph.Factory>().create(audioPlayer, IosStorage(isolatedStorage), localFiles, IosTelemetry.None)

/**
 * Where the graph keeps its preferences and library. [isolatedName] null: the standard defaults and the app's
 * database. Otherwise preferences in the NSUserDefaults suite of that name, and the library in memory.
 */
class IosStorage(
    val isolatedName: String?
)

/**
 * The player's ViewModel, with a fresh `SavedStateHandle`: iOS has no saved state to restore it from, so its open Now
 * Playing panel starts closed each launch. From Swift, `IosAppGraphKt.createPlayerViewModel(graph)`.
 */
fun IosAppGraph.createPlayerViewModel(): PlayerViewModel = playerViewModelFactory.create(SavedStateHandle())
