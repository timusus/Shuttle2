package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LicencesUiState(
    val licences: List<Licence> = emptyList(),
    val loading: Boolean = true
)

@ViewModelKey(LicencesViewModel::class)
@ContributesIntoMap(AppScope::class)
class LicencesViewModel @Inject constructor(
    getLicences: GetLicences
) : ViewModel() {
    private val _uiState = MutableStateFlow(LicencesUiState())
    val uiState: StateFlow<LicencesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = LicencesUiState(licences = getLicences(), loading = false)
        }
    }
}
