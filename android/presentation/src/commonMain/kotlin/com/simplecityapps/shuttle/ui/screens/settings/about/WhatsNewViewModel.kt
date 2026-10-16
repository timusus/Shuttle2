package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WhatsNewUiState(
    val changesets: List<Changeset> = emptyList(),
    val loading: Boolean = true
)

@ViewModelKey(WhatsNewViewModel::class)
@ContributesIntoMap(AppScope::class)
class WhatsNewViewModel @Inject constructor(
    changelogRepository: ChangelogRepository,
    markChangelogViewed: MarkChangelogViewed
) : ViewModel() {
    private val _uiState = MutableStateFlow(WhatsNewUiState())
    val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = WhatsNewUiState(changesets = changelogRepository.changelog(), loading = false)
        }
        markChangelogViewed()
    }
}
