package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionChip
import com.simplecityapps.shuttle.designsystem.component.S2ChoiceChip
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.S2ModalSheet
import com.simplecityapps.shuttle.designsystem.component.S2Scaffold
import com.simplecityapps.shuttle.designsystem.component.S2SelectionToolbar
import com.simplecityapps.shuttle.designsystem.component.S2Switch
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
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
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListEvent
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreCoversViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallRoute
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypePickerRoute
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.LocalTabReselects
import com.simplecityapps.shuttle.ui.shell.SettingsRoute
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the container's chrome shows for the current tab: its selection, which swaps the top bar for the selection toolbar. */
class LibraryTabChrome(
    val selection: MediaSelection? = null,
    val selectedCount: Int = 0,
    val onClearSelection: () -> Unit = {},
)

/**
 * The Library container (inventory §1, #661, #669): a one-row top bar, then a pinned row of section chips over a pager.
 * Each page leads with its tab's controls row (count, sort, grid/list, Shuffle, Play) as its list's first item, so it
 * scrolls away with the page. The selection toolbar replaces the top bar while the current tab has a selection.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    chrome: LibraryTabChrome,
    onTabSelected: (LibraryTab) -> Unit,
    onTabsChanged: (order: List<LibraryTab>, enabled: Set<LibraryTab>) -> Unit,
    onSelectionAction: (MediaActionType) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Days left in the server trial, in its last days only: shown as a chip in the top bar that runs [onOpenTrial]. */
    trialDaysLeft: Int? = null,
    onOpenTrial: () -> Unit = {},
    /** Shown in place of the tabs while the library has no songs (#379). */
    emptyLibrary: (@Composable (Modifier) -> Unit)? = null,
    page: @Composable (LibraryTab) -> Unit,
) {
    val tabs = uiState.tabs
    var editingTabs by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = chrome.selectedCount > 0, onBack = chrome.onClearSelection)

    S2Scaffold(
        modifier = modifier,
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedContent(targetState = chrome.selectedCount > 0, label = "library-top-bar") { selecting ->
                if (selecting) {
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(S2Spacing.small)) {
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
                    S2TopBar(
                        title = stringResource(R.string.title_library),
                        actions = {
                            var menuOpen by remember { mutableStateOf(false) }
                            trialDaysLeft?.let { days ->
                                S2ActionChip(
                                    label = pluralStringResource(R.plurals.library_trial_chip, days, days),
                                    onClick = onOpenTrial,
                                    modifier = Modifier.testTag("library-trial-chip"),
                                )
                            }
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
                                groups = listOf(listOf(S2Action(stringResource(R.string.library_edit_tabs), { editingTabs = true }))),
                            )
                        },
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
            LibraryPager(tabs, uiState.currentTab, chrome, onTabSelected, Modifier.padding(padding), page)
        }
    }

    if (editingTabs) {
        EditTabsSheet(uiState, onTabsChanged, onDismissRequest = { editingTabs = false })
    }
}

@Composable
internal fun selectionAction(type: MediaActionType, icon: ImageVector, onAction: (MediaActionType) -> Unit) = S2Action(type.label(), { onAction(type) }, icon)

@Composable
private fun LibraryPager(
    tabs: List<LibraryTab>,
    currentTab: LibraryTab?,
    chrome: LibraryTabChrome,
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
        // The target page, so a tap or a swipe marks its chip straight away rather than each page it passes.
        LibrarySectionChips(
            tabs = tabs,
            selectedIndex = pagerState.targetPage.coerceIn(0, tabs.lastIndex),
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
        )
        HorizontalPager(state = pagerState, key = { tabs[it] }, modifier = Modifier.fillMaxSize().testTag("library-pager")) { index ->
            // Only the visible section scrolls to the top on a re-select.
            val reselects = if (index == pagerState.currentPage) LocalTabReselects.current else emptyFlow()
            CompositionLocalProvider(LocalTabReselects provides reselects) { page(tabs[index]) }
        }
    }
}

/**
 * The sections as a scrolling row of single-select chips, in the user's order. The row scrolls only when the selected
 * chip is not fully in view, and then by the least that shows it: to the start edge when it's before the window, to the
 * end edge when it's after. The first placement snaps, so the row opens on the selected chip rather than sliding to it.
 */
@Composable
private fun LibrarySectionChips(
    tabs: List<LibraryTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(tabs, selectedIndex) {
        // Once the row holds these tabs, so the screen can open on a section past the row's edge.
        snapshotFlow { listState.layoutInfo }.first { it.viewportSize.width > 0 && it.totalItemsCount == tabs.size }
        // The layout that published it may still be running; scrolling now would remeasure inside it.
        withFrameNanos { }
        // A second pass when the chip was out of the window, whose width the first could only estimate.
        repeat(2) {
            val scrollOffset = listState.layoutInfo.revealScrollOffset(selectedIndex) ?: return@repeat
            if (placed) listState.animateScrollToItem(selectedIndex, scrollOffset) else listState.scrollToItem(selectedIndex, scrollOffset)
        }
        placed = true
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = SectionChipsPadding),
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
        modifier = Modifier.fillMaxWidth().testTag("library-sections"),
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab }) { index, tab ->
            S2ChoiceChip(label = tab.label(), selected = index == selectedIndex, onClick = { onSelect(index) })
        }
    }
}

/**
 * The scroll offset for `scrollToItem(index, offset)` that brings item [index] fully inside the content padding by the
 * least movement, or null when it already is. Offsets run from the start edge, so this holds in either layout direction.
 */
private fun LazyListLayoutInfo.revealScrollOffset(index: Int): Int? {
    val item = visibleItemsInfo.firstOrNull { it.index == index }
    val contentEnd = viewportEndOffset - afterContentPadding
    val beforeWindow = if (item != null) item.offset < 0 else index < (visibleItemsInfo.firstOrNull()?.index ?: 0)
    // Out of the window, the chip's width is unknown until it's laid out; its neighbour's stands in.
    val size = item?.size ?: visibleItemsInfo.lastOrNull()?.size ?: 0
    return when {
        beforeWindow -> 0
        item == null || item.offset + item.size > contentEnd -> -(contentEnd - size).coerceAtLeast(0)
        else -> null
    }
}

private val SectionChipsPadding = S2Spacing.medium

@Composable
fun LibraryTab.label(): String = stringResource(
    when (this) {
        LibraryTab.Songs -> R.string.songs
        LibraryTab.Albums -> R.string.albums
        LibraryTab.Artists -> R.string.artists
        LibraryTab.Playlists -> R.string.library_playlists
        LibraryTab.Genres -> R.string.genres
        LibraryTab.Folders -> R.string.library_tab_folders
    },
)

/** Shows, hides and reorders the tabs; every change saves straight away. */
@Composable
private fun EditTabsSheet(
    uiState: LibraryUiState,
    onTabsChanged: (order: List<LibraryTab>, enabled: Set<LibraryTab>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    S2ModalSheet(onDismissRequest = onDismissRequest) {
        S2Text(stringResource(R.string.library_edit_tabs), modifier = Modifier.padding(horizontal = S2Spacing.large, vertical = S2Spacing.small))
        val order = uiState.allTabs
        order.forEachIndexed { index, tab ->
            val enabled = tab in uiState.enabledTabs
            val setEnabled = { checked: Boolean -> onTabsChanged(order, if (checked) uiState.enabledTabs + tab else uiState.enabledTabs - tab) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library-tab-switch-${tab.name}")
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = setEnabled)
                    .padding(horizontal = S2Spacing.large, vertical = S2Spacing.xsmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium),
            ) {
                S2Switch(checked = enabled, onCheckedChange = null)
                S2Text(tab.label(), modifier = Modifier.weight(1f))
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
    val viewModel: LibraryViewModel = metroViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trialViewModel: LibraryTrialChipViewModel = metroViewModel()
    val trialDaysLeft by trialViewModel.uiState.collectAsStateWithLifecycle()
    val emptyViewModel: LibraryEmptyViewModel = metroViewModel()
    val content by emptyViewModel.uiState.collectAsStateWithLifecycle()
    val accessRequests = rememberMusicAccessRequests(emptyViewModel)
    var connectingServer by rememberSaveable { mutableStateOf(false) }
    if (connectingServer) {
        ServerTypePickerRoute(onDismissRequest = { connectingServer = false })
    }

    // The only tabs that support selection (#225); Genres/Playlists/Folders have none to clear.
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
    val controls = tabControls(tab, actions)
    when (tab) {
        LibraryTab.Songs -> {
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

        LibraryTab.Albums -> {
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

        LibraryTab.Artists -> {
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

        LibraryTab.Genres -> {
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

        LibraryTab.Playlists -> {
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

        LibraryTab.Folders -> {
            val viewModel: FolderListViewModel = metroViewModel()
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

/** A tab's controls row, read from the same ViewModel instance its page uses. */
@Composable
private fun tabControls(tab: LibraryTab, actions: MediaActionsState): LibraryTabControls = when (tab) {
    LibraryTab.Songs -> {
        val viewModel: SongListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val songs = state.songs.takeIf { state.loadingState == SongListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.songsPlural, state.songs.size),
            sortOptions = sortOptions(
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
            onPlay = songs?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Songs(it))) } },
            onShuffle = songs?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(it))) } },
        )
    }

    LibraryTab.Albums -> {
        val viewModel: AlbumListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val albums = state.albums.takeIf { state.loadingState == AlbumListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.albumsPlural, state.albums.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumSortOrder.AlbumName to R.string.menu_title_sort_album_name,
                    AlbumSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                    AlbumSortOrder.Year to R.string.menu_title_sort_year,
                    AlbumSortOrder.Random to R.string.menu_title_sort_random,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = albums?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Albums(it))) } },
            // Album-grouped shuffle has no MediaAction yet; the tab ViewModel keeps it.
            onShuffle = albums?.let { viewModel::onShuffle },
        )
    }

    LibraryTab.Artists -> {
        val viewModel: AlbumArtistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // Play and Shuffle take every listed artist's songs, artist by artist in the current sort.
        val artists = state.albumArtists.takeIf { state.loadingState == AlbumArtistListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_artists, state.albumArtists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumArtistSortOrder.Default to R.string.menu_title_sort_artist_name,
                    AlbumArtistSortOrder.AlbumCount to R.string.menu_title_sort_album_count,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = artists?.let { { actions.dispatch(MediaAction.Play(MediaSelection.AlbumArtists(it))) } },
            onShuffle = artists?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.AlbumArtists(it))) } },
        )
    }

    LibraryTab.Genres -> {
        val viewModel: GenreListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_genres, state.genres.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(GenreSortOrder.Default to R.string.menu_title_sort_genre_name, GenreSortOrder.SongCount to R.string.menu_title_sort_song_count),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Playlists -> {
        val viewModel: PlaylistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_playlists, state.playlists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(PlaylistSortOrder.Name to R.string.menu_title_sort_playlist_name, PlaylistSortOrder.Default to R.string.menu_title_sort_date_created),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Folders -> LibraryTabControls()
}

@Composable
internal fun <T> sortOptions(current: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit): List<S2Action> = options.map { (order, label) ->
    S2Action(stringResource(label), { onSelect(order) }, selected = order == current)
}
