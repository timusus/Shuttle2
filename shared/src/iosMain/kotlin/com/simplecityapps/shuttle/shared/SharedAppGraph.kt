package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.platform.BundledText
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsViewModel
import com.simplecityapps.shuttle.ui.screens.home.HomeViewModel
import com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerViewModel
import com.simplecityapps.shuttle.ui.screens.sources.SourcesViewModel
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The iOS Metro graph over the shared modules' `AppScope` contributions (#586): the ViewModels
 * :android:presentation contributes to the ViewModel maps, its [AppViewModelFactory][com.simplecityapps.shuttle.di.AppViewModelFactory]
 * binding and core's coroutine bindings, merged here from their klibs as Android's `AppGraph` merges them. Swift
 * reaches a ViewModel through a typed property ([shellViewModel]) or [metroViewModelFactory].
 *
 * Swift passes the platform objects to [Factory.create]. The ViewModels whose dependencies iOS can't provide yet
 * (the repositories and media sources the later waves bring) are excluded until it can.
 * [IosAppGraph] becomes this graph once the Swift side creates it.
 *
 * Wave 2's six ViewModels stay excluded too: they need the Song/Album/AlbumArtist/Genre/Playlist repositories,
 * `QueueOperations`/`PlaybackOperations`, `PlatformFeatures`, `SongDownloader` and friends, none of which iOS
 * binds yet. Phase 5's `IosAppGraph` drops these exclusions once it binds the commonMain repositories and
 * `IosPlayerController`.
 *
 * Wave 3's are excluded for the same reason: Home needs the Song/Album/AlbumArtist repositories and
 * `QueueOperations`/`PlaybackOperations`; Sources and the server-type picker need `MediaSources`,
 * `ScannerFolderStore`, `SongImportStateProvider` and `TryAddServer` (the trial's server gate). Each drops out once
 * phase 5 binds them.
 */
@DependencyGraph(
    AppScope::class,
    excludes = [
        ExcludedSongsViewModel::class,
        LibraryEmptyViewModel::class,
        AlbumArtistListViewModel::class,
        AlbumListViewModel::class,
        GenreListViewModel::class,
        SongListViewModel::class,
        PlaylistListViewModel::class,
        MediaActionsViewModel::class,
        HomeViewModel::class,
        ServerTypePickerViewModel::class,
        SourcesViewModel::class,
    ],
)
interface SharedAppGraph : ViewModelGraph {
    val shellViewModel: ShellViewModel

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides keyValueStore: KeyValueStore,
            @Provides bundledText: BundledText,
            @Provides appVersion: AppVersion,
        ): SharedAppGraph
    }
}
