package com.simplecityapps.shuttle.ui.screens.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LicencesUiState(
    val licences: List<Licence> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
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
