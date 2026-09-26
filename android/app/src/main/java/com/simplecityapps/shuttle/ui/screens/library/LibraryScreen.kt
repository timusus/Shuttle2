package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.S2SelectionToolbar
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.CreatePlaylistDialog
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.label
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListEvent
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import kotlinx.coroutines.launch

/** What the container's chrome shows for the current tab: its count, selection and overflow options. */
class LibraryTabChrome(
    val subtitle: String? = null,
    val selection: MediaSelection? = null,
    val selectedCount: Int = 0,
    val onClearSelection: () -> Unit = {},
    /** Sort and view options, one group each, for the overflow above "Edit tabs". */
    val menu: List<List<S2Action>> = emptyList(),
)

/**
 * The Library container (inventory §1): a collapsing large top bar with the current tab's count, a scrollable tab
 * row over a pager, and the selection toolbar swapped in while the current tab has a selection.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    chrome: LibraryTabChrome,
    onTabSelected: (LibraryTab) -> Unit,
    onTabsChanged: (order: List<LibraryTab>, enabled: Set<LibraryTab>) -> Unit,
    onSelectionAction: (MediaActionType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown in place of the tabs while the library has no songs (#379). */
    emptyLibrary: (@Composable (Modifier) -> Unit)? = null,
    page: @Composable (LibraryTab) -> Unit,
) {
    val tabs = uiState.tabs
    var editingTabs by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    BackHandler(enabled = chrome.selectedCount > 0, onBack = chrome.onClearSelection)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedContent(targetState = chrome.selectedCount > 0, label = "library-top-bar") { selecting ->
                if (selecting) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                        S2SelectionToolbar(
                            selectedCount = chrome.selectedCount,
                            onClearSelection = chrome.onClearSelection,
                            actions = listOf(
                                selectionAction(MediaActionType.Play, Icons.Rounded.PlayArrow, onSelectionAction),
                                selectionAction(MediaActionType.AddToQueue, Icons.AutoMirrored.Rounded.QueueMusic, onSelectionAction),
                                selectionAction(MediaActionType.AddToPlaylist, Icons.AutoMirrored.Rounded.PlaylistAdd, onSelectionAction),
                            ),
                            overflowActions = listOf(
                                listOf(
                                    selectionAction(MediaActionType.PlayNext, Icons.Rounded.SkipNext, onSelectionAction),
                                    selectionAction(MediaActionType.Shuffle, Icons.Rounded.Shuffle, onSelectionAction),
                                ),
                                listOfNotNull(
                                    // Batch tag editing, when every selected item's provider can write tags.
                                    selectionAction(MediaActionType.EditTags, Icons.Rounded.Edit, onSelectionAction)
                                        .takeIf { chrome.selection?.mediaProviders?.let { providers -> providers.isNotEmpty() && providers.all { it.supportsTagEditing } } == true },
                                    selectionAction(MediaActionType.Exclude, Icons.Rounded.Block, onSelectionAction),
                                ),
                            ),
                        )
                    }
                } else {
                    LargeFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.title_library)) },
                        subtitle = chrome.subtitle?.let { { Text(it) } },
                        actions = {
                            var menuOpen by remember { mutableStateOf(false) }
                            S2IconButton(icon = Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_menu_settings), onClick = onOpenSettings)
                            S2IconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.library_more_options),
                                onClick = { menuOpen = true },
                                modifier = Modifier.testTag("library-more"),
                            )
                            S2Menu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                groups = chrome.menu + listOf(listOf(S2Action(stringResource(R.string.library_edit_tabs), { editingTabs = true }))),
                            )
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
    ) { padding ->
        if (emptyLibrary != null) {
            emptyLibrary(Modifier.padding(padding).fillMaxSize())
        } else if (tabs.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.library_all_tabs_hidden),
                message = stringResource(R.string.library_tabs_empty),
                action = StateAction(stringResource(R.string.library_edit_tabs)) { editingTabs = true },
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LibraryPager(tabs, uiState.currentTab, onTabSelected, Modifier.padding(padding), page)
        }
    }

    if (editingTabs) {
        EditTabsSheet(uiState, onTabsChanged, onDismissRequest = { editingTabs = false })
    }
}

@Composable
internal fun selectionAction(type: MediaActionType, icon: ImageVector, onAction: (MediaActionType) -> Unit) = S2Action(type.label(), { onAction(type) }, icon)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPager(
    tabs: List<LibraryTab>,
    currentTab: LibraryTab?,
    onTabSelected: (LibraryTab) -> Unit,
    modifier: Modifier,
    page: @Composable (LibraryTab) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(currentTab).coerceAtLeast(0)) { tabs.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.settledPage }.collect { index -> tabs.getOrNull(index)?.let(onTabSelected) }
    }
    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex), modifier = Modifier.testTag("library-tabs")) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label()) },
                )
            }
        }
        HorizontalPager(state = pagerState, key = { tabs[it] }, modifier = Modifier.fillMaxSize()) { index -> page(tabs[index]) }
    }
}

@Composable
fun LibraryTab.label(): String = stringResource(
    when (this) {
        LibraryTab.Genres -> R.string.genres
        LibraryTab.Playlists -> R.string.library_playlists
        LibraryTab.Artists -> R.string.artists
        LibraryTab.Albums -> R.string.albums
        LibraryTab.Songs -> R.string.songs
        LibraryTab.Folders -> R.string.library_tab_folders
    },
)

/** Shows, hides and reorders the tabs; every change saves straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTabsSheet(
    uiState: LibraryUiState,
    onTabsChanged: (order: List<LibraryTab>, enabled: Set<LibraryTab>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Text(stringResource(R.string.library_edit_tabs), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val order = uiState.allTabs
        order.forEachIndexed { index, tab ->
            val enabled = tab in uiState.enabledTabs
            val setEnabled = { checked: Boolean -> onTabsChanged(order, if (checked) uiState.enabledTabs + tab else uiState.enabledTabs - tab) }
            ListItem(
                onClick = { setEnabled(!enabled) },
                leadingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = setEnabled,
                        modifier = Modifier.testTag("library-tab-switch-${tab.name}"),
                    )
                },
                trailingContent = {
                    Row {
                        S2IconButton(
                            icon = Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.library_tab_move_up),
                            enabled = index > 0,
                            onClick = { onTabsChanged(order.move(index, index - 1), uiState.enabledTabs) },
                        )
                        S2IconButton(
                            icon = Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.library_tab_move_down),
                            enabled = index < order.lastIndex,
                            onClick = { onTabsChanged(order.move(index, index + 1), uiState.enabledTabs) },
                        )
                    }
                },
            ) {
                Text(tab.label())
            }
        }
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }

/**
 * The Library destination: wires [LibraryScreen] to [LibraryViewModel], the existing tab ViewModels and the shared
 * media actions. Every tab ViewModel lives in this nav entry's store, so pages and chrome share instances.
 */
@Composable
fun LibraryDestination(
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val emptyViewModel: LibraryEmptyViewModel = hiltViewModel()
    val content by emptyViewModel.uiState.collectAsStateWithLifecycle()
    val accessRequests = rememberMusicAccessRequests(emptyViewModel)

    // The only tabs that support selection (#225); Genres/Playlists/Folders have none to clear.
    val songViewModel: SongListViewModel = hiltViewModel()
    val albumViewModel: AlbumListViewModel = hiltViewModel()
    val artistViewModel: AlbumArtistListViewModel = hiltViewModel()
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
            emptyLibrary = (content as? LibraryAvailability.Empty)?.let { empty ->
                @Composable { modifier: Modifier ->
                    LibraryEmptyScreen(
                        state = empty,
                        onAllowAccess = accessRequests.request,
                        onOpenAppSettings = accessRequests.openAppSettings,
                        onScan = emptyViewModel::onScan,
                        onConnectServer = { onOpen(SettingsDestinationRoute(SettingsDestination.Sources)) },
                        modifier = modifier,
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
    when (tab) {
        LibraryTab.Songs -> {
            val viewModel: SongListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SongsPage(
                state = state,
                onSongClick = { song ->
                    if (state.isSelecting) viewModel.onSongClick(song) else actions.dispatch(MediaAction.Play(MediaSelection.Songs(state.songs), state.songs.indexOf(song)))
                },
                onSongLongClick = viewModel::onSongLongClick,
                onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
                onPlay = { actions.dispatch(MediaAction.Play(MediaSelection.Songs(state.songs))) },
                onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(state.songs))) },
            )
        }

        LibraryTab.Albums -> {
            val viewModel: AlbumListViewModel = hiltViewModel()
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
                onPlay = { actions.dispatch(MediaAction.Play(MediaSelection.Albums(state.albums))) },
                // Album-grouped shuffle has no MediaAction yet; the tab ViewModel keeps it.
                onShuffle = viewModel::onShuffle,
            )
        }

        LibraryTab.Artists -> {
            val viewModel: AlbumArtistListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            ArtistsPage(
                state = state,
                onArtistClick = { artist -> if (state.isSelecting) viewModel.onArtistClick(artist) else onOpen(artist.route) },
                onArtistLongClick = viewModel::onArtistLongClick,
                onArtistMore = { artist ->
                    actions.showActions(MediaActionsTarget(artist.name ?: artist.friendlyArtistName.orEmpty(), null, MediaSelection.AlbumArtists(artist), ArtworkPlaceholder.Artist))
                },
            )
        }

        LibraryTab.Genres -> {
            val viewModel: GenreListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GenresPage(
                state = state,
                onGenreClick = { genre -> onOpen(GenreRoute(genre.name)) },
                onGenreMore = { genre -> actions.showActions(MediaActionsTarget(genre.name, null, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)) },
            )
        }

        LibraryTab.Playlists -> {
            val viewModel: PlaylistListViewModel = hiltViewModel()
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
                onSmartPlaylistClick = { smartPlaylist -> smartPlaylist.route()?.let(onOpen) },
                onNewPlaylist = { creating = true },
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

        LibraryTab.Folders -> {
            val viewModel: FolderListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BackHandler(enabled = state.canNavigateUp, onBack = viewModel::onNavigateUp)
            FoldersPage(
                state = state,
                onFolderClick = viewModel::onFolderClick,
                onFolderMore = { folder -> actions.showActions(MediaActionsTarget(folder.name, null, MediaSelection.Folders(listOf(folder.path)), ArtworkPlaceholder.Folder)) },
                onSongClick = { song -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(state.songs), state.songs.indexOf(song))) },
                onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
                onNavigateUp = viewModel::onNavigateUp,
            )
        }
    }
}

/** The current tab's chrome, read from the same ViewModel instance its page uses. */
@Composable
private fun tabChrome(tab: LibraryTab?): LibraryTabChrome = when (tab) {
    LibraryTab.Songs -> {
        val viewModel: SongListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(
            subtitle = pluralString(R.plurals.songsPlural, state.songs.size),
            selection = MediaSelection.Songs(state.selectedSongs.toList()),
            selectedCount = state.selectedSongs.size,
            onClearSelection = viewModel::clearSelection,
            menu = listOf(
                sortOptions(
                    state.sortOrder,
                    listOf(
                        SongSortOrder.SongName to R.string.menu_title_sort_song_name,
                        SongSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                        SongSortOrder.AlbumGroupKey to R.string.menu_title_sort_album_name,
                        SongSortOrder.Year to R.string.menu_title_sort_year,
                        SongSortOrder.Duration to R.string.menu_title_sort_duration,
                        SongSortOrder.LastModified to R.string.menu_title_sort_date_modified,
                    ),
                    viewModel::setSortOrder,
                ),
            ),
        )
    }

    LibraryTab.Albums -> {
        val viewModel: AlbumListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(
            subtitle = pluralString(R.plurals.albumsPlural, state.albums.size),
            selection = MediaSelection.Albums(state.selectedAlbums.toList()),
            selectedCount = state.selectedAlbums.size,
            onClearSelection = viewModel::clearSelection,
            menu = listOf(
                sortOptions(
                    state.sortOrder,
                    listOf(
                        AlbumSortOrder.AlbumName to R.string.menu_title_sort_album_name,
                        AlbumSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                        AlbumSortOrder.Year to R.string.menu_title_sort_year,
                        AlbumSortOrder.Random to R.string.menu_title_sort_random,
                    ),
                    viewModel::setSortOrder,
                ),
                viewModeOptions(state.viewMode, viewModel::setViewMode),
            ),
        )
    }

    LibraryTab.Artists -> {
        val viewModel: AlbumArtistListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(
            subtitle = pluralString(R.plurals.library_count_artists, state.albumArtists.size),
            selection = MediaSelection.AlbumArtists(state.selectedArtists.toList()),
            selectedCount = state.selectedArtists.size,
            onClearSelection = viewModel::clearSelection,
            menu = listOf(viewModeOptions(state.viewMode, viewModel::setViewMode)),
        )
    }

    LibraryTab.Genres -> {
        val viewModel: GenreListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(
            subtitle = pluralString(R.plurals.library_count_genres, state.genres.size),
            menu = listOf(
                sortOptions(
                    state.sortOrder,
                    listOf(GenreSortOrder.Default to R.string.menu_title_sort_genre_name, GenreSortOrder.SongCount to R.string.menu_title_sort_song_count),
                    viewModel::setSortOrder,
                ),
            ),
        )
    }

    LibraryTab.Playlists -> {
        val viewModel: PlaylistListViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabChrome(
            subtitle = pluralString(R.plurals.library_count_playlists, state.playlists.size),
            menu = listOf(
                sortOptions(
                    state.sortOrder,
                    listOf(PlaylistSortOrder.Name to R.string.menu_title_sort_playlist_name, PlaylistSortOrder.Default to R.string.menu_title_sort_date_created),
                    viewModel::setSortOrder,
                ),
            ),
        )
    }

    LibraryTab.Folders, null -> LibraryTabChrome()
}

@Composable
internal fun <T> sortOptions(current: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit): List<S2Action> = options.map { (order, label) ->
    S2Action(stringResource(label), { onSelect(order) }, selected = order == current)
}

@Composable
private fun viewModeOptions(current: ViewMode, onSelect: (ViewMode) -> Unit): List<S2Action> = listOf(
    S2Action(stringResource(R.string.menu_title_view_mode_list), { onSelect(ViewMode.List) }, selected = current == ViewMode.List),
    S2Action(stringResource(R.string.menu_title_view_mode_grid), { onSelect(ViewMode.Grid) }, selected = current == ViewMode.Grid),
)
