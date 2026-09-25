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
    subtitle = "$selectedCount selected",
    selection = selection,
    selectedCount = selectedCount,
)

fun chromeWithMenu(subtitle: String? = null, menu: List<List<S2Action>> = emptyList()) = LibraryTabChrome(subtitle = subtitle, menu = menu)
