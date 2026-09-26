package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WhatsNewUiState(
    val changesets: List<Changeset> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    getChangelog: GetChangelog,
    generalPreferenceManager: GeneralPreferenceManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(WhatsNewUiState())
    val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = WhatsNewUiState(changesets = getChangelog(), loading = false)
        }
        generalPreferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }
}
