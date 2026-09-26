package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.persistence.LibraryTab

/**
 * Clears a tab's active multi-select selection when the user switches away from it, or leaves the Library
 * destination entirely (#225, #456) — each tab's ViewModel keeps its own selection for as long as the
 * destination's nav entry lives, so nothing else clears it on a tab switch or when the destination is disposed.
 * [LibraryDestination] owns one instance, so the rule lives here once instead of in every selectable tab's
 * ViewModel.
 */
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

    /** Call when the Library destination leaves composition; clears whichever tab was showing. */
    fun onDestinationLeft() {
        currentTab?.let(clearSelection)
        currentTab = null
    }
}
