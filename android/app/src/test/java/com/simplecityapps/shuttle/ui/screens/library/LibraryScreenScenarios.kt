package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.actions.MediaSelection

/** The container with [enabledTabs] shown in [allTabs] order, on [currentTab]. */
fun libraryState(
    enabledTabs: Set<LibraryTab> = LibraryTab.defaultEnabled.toSet(),
    allTabs: List<LibraryTab> = LibraryTab.entries,
    currentTab: LibraryTab? = allTabs.firstOrNull { it in enabledTabs },
) = LibraryUiState(allTabs = allTabs, enabledTabs = enabledTabs, currentTab = currentTab)

fun hiddenTabsLibrary() = libraryState(enabledTabs = emptySet())

/** Chrome for a tab with [selectedCount] items selected. */
fun selectingChrome(selectedCount: Int = 2, selection: MediaSelection? = null) = LibraryTabChrome(
    selection = selection,
    selectedCount = selectedCount,
)

/** A tab's controls row; a non-null [onPlay] / [onShuffle] shows that button. */
fun libraryControls(
    count: String? = null,
    sortOptions: List<S2Action> = emptyList(),
    viewMode: ViewMode? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
) = LibraryTabControls(count = count, sortOptions = sortOptions, viewMode = viewMode, onPlay = onPlay, onShuffle = onShuffle)

/** Sort options named [labels], [selected] the current one. */
fun sorts(vararg labels: String, selected: String = labels.first()) = labels.map { S2Action(it, {}, selected = it == selected) }
