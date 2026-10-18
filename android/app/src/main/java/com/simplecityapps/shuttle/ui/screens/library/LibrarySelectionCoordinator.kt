package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.persistence.LibraryTab

/** Clears a tab's selection on tab switch only, so rotation and opening a detail screen keep it. */
class LibrarySelectionCoordinator(
    private val clearSelection: (LibraryTab) -> Unit,
) {
    private var currentTab: LibraryTab? = null

    /** Call whenever the current tab changes; clears the tab being left, if any. */
    fun onTabChanged(tab: LibraryTab?) {
        val previous = currentTab
        currentTab = tab
        if (previous != null && previous != tab) clearSelection(previous)
    }
}
