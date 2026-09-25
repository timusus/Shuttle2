package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LibraryUiState(
    /** Every tab in the user's order, shown or not: the Edit tabs sheet lists these. */
    val allTabs: List<LibraryTab> = LibraryTab.entries,
    val enabledTabs: Set<LibraryTab> = LibraryTab.defaultEnabled.toSet(),
    val currentTab: LibraryTab? = null,
) {
    /** The tabs the pager shows, in order. */
    val tabs: List<LibraryTab> get() = allTabs.filter { it in enabledTabs }
}

/**
 * The library container: which tabs show, in what order, and which one is current. All three persist in the same
 * preferences the legacy LibraryFragment used, so the choice carries across.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun onTabSelected(tab: LibraryTab) {
        if (tab == _uiState.value.currentTab) return
        preferences.currentLibraryTab = tab
        _uiState.update { it.copy(currentTab = tab) }
    }

    /** Saves the Edit tabs sheet: [order] is every tab, [enabled] the ones shown. */
    fun onTabsChanged(order: List<LibraryTab>, enabled: Set<LibraryTab>) {
        preferences.allLibraryTabs = order
        preferences.enabledLibraryTabs = order.filter { it in enabled }
        _uiState.value = load()
    }

    private fun load(): LibraryUiState {
        val state = LibraryUiState(allTabs = preferences.allLibraryTabs, enabledTabs = preferences.enabledLibraryTabs.toSet())
        // Artists is the legacy default when nothing was saved, or the saved tab has since been hidden.
        val current = preferences.currentLibraryTab?.takeIf { it in state.tabs }
            ?: LibraryTab.Artists.takeIf { it in state.tabs }
            ?: state.tabs.firstOrNull()
        return state.copy(currentTab = current)
    }
}
