package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.persistence.LibraryTab

/**
 * Clears a tab's active multi-select selection when the user switches away from it (#225, #456). Each tab's
 * ViewModel keeps its own selection for as long as the destination's nav entry lives, so nothing else clears
 * it on a tab switch. Leaving composition deliberately clears nothing: rotation and pushing a detail pane
 * dispose the destination while its entry, and the user's selection, should survive.
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
}
