package com.simplecityapps.shuttle.ui.shell

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.simplecityapps.shuttle.ui.screens.home.homeEntries
import com.simplecityapps.shuttle.ui.screens.library.libraryEntries
import com.simplecityapps.shuttle.ui.screens.search.searchEntries
import com.simplecityapps.shuttle.ui.screens.settings.settingsEntries

/** Routes each key to its screen; the navigator stays out of the screens. */
fun shellEntryProvider(navigator: AppNavigator): (NavKey) -> NavEntry<NavKey> = entryProvider {
    homeEntries(navigator)
    libraryEntries(navigator)
    searchEntries(navigator)
    settingsEntries(navigator)
}
