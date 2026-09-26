package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.LibraryTab
import javax.inject.Inject

/** The library container's saved tabs: every tab in the user's order, the ones shown, and the one last open. */
data class LibraryTabs(
    val all: List<LibraryTab>,
    val enabled: List<LibraryTab>,
    val current: LibraryTab?,
)

/** The saved [LibraryTabs], in the preferences the pre-redesign library screen used, so the choice carries across. */
class ReadLibraryTabs @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) {
    operator fun invoke(): LibraryTabs = LibraryTabs(preferences.allLibraryTabs, preferences.enabledLibraryTabs, preferences.currentLibraryTab)
}

/** Saves the Edit tabs sheet: [order] is every tab, [enabled] the ones shown. */
class SaveLibraryTabs @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) {
    operator fun invoke(order: List<LibraryTab>, enabled: Set<LibraryTab>) {
        preferences.allLibraryTabs = order
        preferences.enabledLibraryTabs = order.filter { it in enabled }
    }
}

/** Saves the tab the library opens on next time. */
class SaveCurrentLibraryTab @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) {
    operator fun invoke(tab: LibraryTab) {
        preferences.currentLibraryTab = tab
    }
}
