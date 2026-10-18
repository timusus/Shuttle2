package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.CreatePlaylistDialog
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListEvent
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallRoute
import com.simplecityapps.shuttle.ui.screens.settings.ThisDeviceRoute
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerRoute
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel

/**
 * The Library destination: wires [LibraryScreen] to [LibraryViewModel], the existing tab ViewModels and the shared
 * media actions. Every tab ViewModel lives in this nav entry's store, so pages and chrome share instances.
 */
@Composable
fun LibraryDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel: LibraryViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val offlineViewModel: OfflineModeViewModel = metroViewModel()
    val offlineMode by offlineViewModel.uiState.collectAsStateWithLifecycle()
    val trialViewModel: LibraryTrialChipViewModel = metroViewModel()
    val trialDaysLeft by trialViewModel.uiState.collectAsStateWithLifecycle()
    val trialOffer = rememberTrialOffer()
    val emptyViewModel: LibraryEmptyViewModel = metroViewModel()
    val content by emptyViewModel.uiState.collectAsStateWithLifecycle()
    val accessRequests = rememberMusicAccessRequests(emptyViewModel)
    var connectingServer by rememberSaveable { mutableStateOf(false) }
    if (connectingServer) {
        ServerTypePickerRoute(onDismissRequest = { connectingServer = false })
    }

    // The only tabs that support selection; Genres/Playlists/Folders have none to clear.
    val songViewModel: SongListViewModel = metroViewModel()
    val albumViewModel: AlbumListViewModel = metroViewModel()
    val artistViewModel: AlbumArtistListViewModel = metroViewModel()
    val selectionCoordinator = remember(songViewModel, albumViewModel, artistViewModel) {
        LibrarySelectionCoordinator { tab ->
            when (tab) {
                LibraryTab.Songs -> songViewModel.clearSelection()
                LibraryTab.Albums -> albumViewModel.clearSelection()
                LibraryTab.Artists -> artistViewModel.clearSelection()
                LibraryTab.Genres, LibraryTab.Playlists, LibraryTab.Folders -> {}
            }
        }
    }
    LaunchedEffect(uiState.currentTab) { selectionCoordinator.onTabChanged(uiState.currentTab) }

    MediaActionsHost(onNavigate = onNavigate) { actions ->
        val chrome = tabChrome(uiState.currentTab)
        LibraryScreen(
            uiState = uiState,
            chrome = chrome,
            onTabSelected = viewModel::onTabSelected,
            onTabsChanged = viewModel::onTabsChanged,
            onSelectionAction = { type ->
                chrome.selection?.let { actions.perform(type, it) }
                chrome.onClearSelection()
            },
            onOpenSettings = { onOpen(SettingsRoute) },
            offlineMode = offlineMode,
            onToggleOffline = offlineViewModel::toggle,
            trialDaysLeft = trialDaysLeft,
            onOpenTrial = { onOpen(PaywallRoute(PaywallSource.LibraryTrialChip)) },
            emptyLibrary = (content as? LibraryAvailability.Empty)?.let { empty ->
                @Composable { modifier: Modifier ->
                    LibraryEmptyScreen(
                        state = empty,
                        onAllowAccess = accessRequests.request,
                        onOpenAppSettings = accessRequests.openAppSettings,
                        onScan = emptyViewModel::onScan,
                        onConnectServer = { connectingServer = true },
                        onAddFolder = { onOpen(ThisDeviceRoute) },
                        modifier = modifier,
                        trialOffer = trialOffer,
                    )
                }
            },
        ) { tab -> LibraryPage(tab, actions, onOpen) }
    }
}

@Composable
private fun LibraryPage(
    tab: LibraryTab,
    actions: MediaActionsState,
    onOpen: (NavKey) -> Unit,
) {
    val controls = tabControls(tab, actions)
    when (tab) {
        LibraryTab.Songs -> SongsTab(actions, controls)
        LibraryTab.Albums -> AlbumsTab(actions, controls, onOpen)
        LibraryTab.Artists -> ArtistsTab(actions, controls, onOpen)
        LibraryTab.Genres -> GenresTab(actions, controls, onOpen)
        LibraryTab.Playlists -> PlaylistsTab(actions, controls, onOpen)
        LibraryTab.Folders -> FoldersTab(actions, onOpen)
    }
}

@Composable
private fun SongsTab(
    actions: MediaActionsState,
    controls: LibraryTabControls,
) {
    val viewModel: SongListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SongsPage(
        state = state,
        onSongClick = { song ->
            if (state.isSelecting) viewModel.onSongClick(song) else actions.dispatch(MediaAction.Play(MediaSelection.Songs(state.songs), state.songs.indexOf(song)))
        },
        onSongLongClick = viewModel::onSongLongClick,
        onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
        controls = controls,
    )
}

@Composable
private fun AlbumsTab(
    actions: MediaActionsState,
    controls: LibraryTabControls,
    onOpen: (NavKey) -> Unit,
) {
    val viewModel: AlbumListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalShellSnackbarHostState.current
    val resources = LocalResources.current
    ConsumeEvents(state.events, viewModel::onEventHandled) { event ->
        when (event) {
            is AlbumListEvent.ShuffleFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.shuffle_albums_failed, event.reason ?: resources.getString(R.string.error_unknown)),
            )
        }
    }
    AlbumsPage(
        state = state,
        onAlbumClick = { album -> if (state.isSelecting) viewModel.onAlbumClick(album) else onOpen(album.route) },
        onAlbumLongClick = viewModel::onAlbumLongClick,
        onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
        controls = controls,
    )
}

@Composable
private fun ArtistsTab(
    actions: MediaActionsState,
    controls: LibraryTabControls,
    onOpen: (NavKey) -> Unit,
) {
    val viewModel: AlbumArtistListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ArtistsPage(
        state = state,
        onArtistClick = { artist -> if (state.isSelecting) viewModel.onArtistClick(artist) else onOpen(artist.route) },
        onArtistLongClick = viewModel::onArtistLongClick,
        onArtistMore = { artist ->
            actions.showActions(MediaActionsTarget(artist.name ?: artist.friendlyArtistName.orEmpty(), null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist))
        },
        controls = controls,
    )
}

@Composable
private fun GenresTab(
    actions: MediaActionsState,
    controls: LibraryTabControls,
    onOpen: (NavKey) -> Unit,
) {
    val viewModel: GenreListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val coversViewModel: GenreCoversViewModel = metroViewModel()
    val covers by coversViewModel.uiState.collectAsStateWithLifecycle()
    GenresPage(
        state = state,
        covers = covers,
        onGenreClick = { genre -> onOpen(GenreRoute(genre.name)) },
        onGenreMore = { genre -> actions.showActions(MediaActionsTarget(genre.name, null, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)) },
        controls = controls,
    )
}

@Composable
private fun PlaylistsTab(
    actions: MediaActionsState,
    controls: LibraryTabControls,
    onOpen: (NavKey) -> Unit,
) {
    val viewModel: PlaylistListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
    PlaylistsPage(
        state = state,
        onPlaylistClick = { playlist -> onOpen(PlaylistRoute(playlist.id)) },
        onPlaylistMore = { playlist ->
            actions.showActions(
                MediaActionsTarget(
                    title = playlist.name,
                    subtitle = null,
                    selection = MediaSelection.Playlists(playlist),
                    placeholder = ArtworkPlaceholder.Playlist,
                    extraActions = playlistManageActions(resources, playlist) { dialog = it },
                ),
            )
        },
        onSmartPlaylistClick = { smartPlaylist -> onOpen(smartPlaylist.route()) },
        onNewPlaylist = { creating = true },
        controls = controls,
    )
    PlaylistDialogHost(
        dialog = dialog,
        onRename = viewModel::onRename,
        onClear = viewModel::onClear,
        onDelete = viewModel::onDelete,
        onDismissRequest = { dialog = null },
    )
    if (creating) CreatePlaylistDialog(onCreate = viewModel::onCreatePlaylist, onDismissRequest = { creating = false })
}

@Composable
private fun FoldersTab(
    actions: MediaActionsState,
    onOpen: (NavKey) -> Unit,
) {
    val viewModel: FolderListViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.canNavigateUp, onBack = viewModel::onNavigateUp)
    FoldersPage(
        state = state,
        onFolderClick = viewModel::onFolderClick,
        onFolderMore = { folder -> actions.showActions(MediaActionsTarget(folder.name, null, MediaSelection.Folders(listOf(folder.path)), ArtworkPlaceholder.Folder)) },
        onSongClick = { song -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(state.songs), state.songs.indexOf(song))) },
        onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
        onRootClick = viewModel::onRootClick,
        onAddFolder = { onOpen(ThisDeviceRoute) },
    )
}

/** The current tab's chrome, read from the same ViewModel instance its page uses. */
@Composable
private fun tabChrome(tab: LibraryTab?): LibraryTabChrome = when (tab) {
    LibraryTab.Songs -> {
        val viewModel: SongListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(MediaSelection.Songs(state.selectedSongs.toList()), state.selectedSongs.size, viewModel::clearSelection)
    }

    LibraryTab.Albums -> {
        val viewModel: AlbumListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(MediaSelection.Albums(state.selectedAlbums.toList()), state.selectedAlbums.size, viewModel::clearSelection)
    }

    LibraryTab.Artists -> {
        val viewModel: AlbumArtistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(MediaSelection.AlbumArtists(state.selectedArtists.toList()), state.selectedArtists.size, viewModel::clearSelection)
    }

    LibraryTab.Genres, LibraryTab.Playlists, LibraryTab.Folders, null -> LibraryTabChrome()
}
