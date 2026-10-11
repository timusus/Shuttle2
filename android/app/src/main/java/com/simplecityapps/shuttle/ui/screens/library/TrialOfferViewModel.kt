package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.StartTrial
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The trial offer on the empty Library: shown to a user with neither Pro nor a used trial, started only by their tap. */
@ViewModelKey(TrialOfferViewModel::class)
@ContributesIntoMap(AppScope::class)
class TrialOfferViewModel @Inject constructor(
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>,
    private val startTrial: StartTrial
) : ViewModel() {
    val uiState: StateFlow<Boolean> = entitlement
        .map { it.trialAvailable() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), entitlement.value.trialAvailable())

    fun onStartTrial() {
        viewModelScope.launch { startTrial() }
    }
}

private fun Entitlement.trialAvailable() = this is Entitlement.Free && !trialUsed
