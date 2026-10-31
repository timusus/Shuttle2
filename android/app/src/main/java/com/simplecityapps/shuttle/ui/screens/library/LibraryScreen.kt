package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionChip
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2ModalSheet
import com.simplecityapps.shuttle.designsystem.component.S2Scaffold
import com.simplecityapps.shuttle.designsystem.component.S2SelectionToolbar
import com.simplecityapps.shuttle.designsystem.component.S2Switch
import com.simplecityapps.shuttle.designsystem.component.S2TabRow
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.S2TopBar
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.label
import com.simplecityapps.shuttle.ui.shell.LocalTabReselects
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** What the container's chrome shows for the current tab: its selection, which swaps the top bar for the selection toolbar. */
class LibraryTabChrome(
    val selection: MediaSelection? = null,
    val selectedCount: Int = 0,
    val onClearSelection: () -> Unit = {},
)

/**
 * The Library container: a pinned top bar, then a pinned row of section tabs over a pager.
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
    /** Whether Offline mode is on; the top bar's cloud icon shows it and runs [onToggleOffline]. */
    offlineMode: Boolean = false,
    onToggleOffline: () -> Unit = {},
    /** Shown in place of the tabs while the library has no songs. */
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
            LibraryTopBar(
                chrome = chrome,
                trialDaysLeft = trialDaysLeft,
                onOpenTrial = onOpenTrial,
                offlineMode = offlineMode,
                onToggleOffline = onToggleOffline,
                emptyLibrary = emptyLibrary != null,
                onEditTabs = { editingTabs = true },
                onOpenSettings = onOpenSettings,
                onSelectionAction = onSelectionAction,
            )
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

/** The selection toolbar while the tab has a selection, else the pinned bar with the chrome actions. */
@Composable
private fun LibraryTopBar(
    chrome: LibraryTabChrome,
    trialDaysLeft: Int?,
    onOpenTrial: () -> Unit,
    offlineMode: Boolean,
    onToggleOffline: () -> Unit,
    emptyLibrary: Boolean,
    onEditTabs: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectionAction: (MediaActionType) -> Unit,
) {
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
            val barActions: @Composable RowScope.() -> Unit = {
                trialDaysLeft?.let { days ->
                    S2ActionChip(
                        label = pluralStringResource(R.plurals.library_trial_chip, days, days),
                        onClick = onOpenTrial,
                        modifier = Modifier.testTag("library-trial-chip"),
                    )
                }
                // With no songs there is nothing to take offline.
                if (!emptyLibrary) {
                    S2IconButton(
                        icon = if (offlineMode) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                        contentDescription = stringResource(if (offlineMode) R.string.library_offline_mode_on else R.string.library_offline_mode_off),
                        onClick = onToggleOffline,
                        modifier = Modifier.testTag("library-offline-mode"),
                    )
                }
                if (!emptyLibrary) {
                    S2IconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.library_edit_tabs),
                        onClick = onEditTabs,
                        modifier = Modifier.testTag("library-edit-tabs"),
                    )
                }
                S2IconButton(icon = Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_menu_settings), onClick = onOpenSettings)
            }
            S2TopBar(title = stringResource(R.string.title_library), actions = barActions)
        }
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
        // The target page, so a tap or a swipe marks its tab straight away rather than each page it passes.
        val labels = tabs.map { it.label() }
        S2TabRow(
            labels = labels,
            selectedIndex = pagerState.targetPage.coerceIn(0, tabs.lastIndex),
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.testTag("library-sections"),
            indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        )
        HorizontalPager(state = pagerState, key = { tabs[it] }, modifier = Modifier.fillMaxSize().testTag("library-pager")) { index ->
            // Only the visible section scrolls to the top on a re-select.
            val reselects = if (index == pagerState.currentPage) LocalTabReselects.current else emptyFlow()
            CompositionLocalProvider(LocalTabReselects provides reselects) { page(tabs[index]) }
        }
    }
}

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
                    .heightIn(min = S2TouchTarget.minimum)
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
