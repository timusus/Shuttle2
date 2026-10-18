package com.simplecityapps.shuttle.shared

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
import com.simplecityapps.shuttle.ui.screens.search.SearchViewModel
import com.simplecityapps.shuttle.ui.screens.settings.SettingsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.LicencesViewModel
import com.simplecityapps.shuttle.ui.screens.settings.about.WhatsNewViewModel
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.EqualizerViewModel
import com.simplecityapps.shuttle.ui.screens.settings.excluded.ExcludedSongsViewModel
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.scrobbling.ScrobblingViewModel
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoViewModel
import com.simplecityapps.shuttle.ui.screens.sources.FolderRulesViewModel
import com.simplecityapps.shuttle.ui.screens.sources.SourcesViewModel
import com.simplecityapps.shuttle.ui.screens.sources.servers.ServerSignInViewModel
import com.simplecityapps.shuttle.ui.shell.ShellViewModel
import com.simplecityapps.shuttle.ui.shell.player.PlayerViewModel

/** The screens' ViewModels and the assisted factories of those with arguments; part of [IosAppGraph]. */
interface IosViewModelAccessors {
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
    val folderRulesViewModel: FolderRulesViewModel
    val sourceSetupViewModel: SourceSetupViewModel
    val settingsViewModel: SettingsViewModel
    val scrobblingViewModel: ScrobblingViewModel
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

    /** A Jellyfin, Emby or Plex server's sign-in form, including Jellyfin Quick Connect and Plex's plex.tv PIN. */
    val serverSignInViewModelFactory: ServerSignInViewModel.Factory

    /**
     * The mini player and Now Playing's ViewModel. Swift builds one through [createPlayerViewModel], which hands it the
     * saved state it needs.
     */
    val playerViewModelFactory: PlayerViewModel.Factory
}
