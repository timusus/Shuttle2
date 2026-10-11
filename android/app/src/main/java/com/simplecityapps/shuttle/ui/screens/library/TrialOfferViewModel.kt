package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.StartTrial
import com.simplecityapps.shuttle.entitlement.TrialOfferDismissal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The trial offer on the empty Library: shown to a user with neither Pro nor a used trial who hasn't said "Not now",
 * started only by their tap. The paywall and the gates still offer the trial after a dismissal.
 */
@ViewModelKey(TrialOfferViewModel::class)
@ContributesIntoMap(AppScope::class)
class TrialOfferViewModel @Inject constructor(
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>,
    private val startTrial: StartTrial,
    private val dismissal: TrialOfferDismissal
) : ViewModel() {
    private val dismissed = MutableStateFlow(dismissal.dismissed)

    val uiState: StateFlow<Boolean> = combine(entitlement, dismissed) { entitlement, dismissed -> entitlement.trialAvailable() && !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), entitlement.value.trialAvailable() && !dismissed.value)

    fun onStartTrial() {
        viewModelScope.launch { startTrial() }
    }

    fun onDismiss() {
        dismissal.dismiss()
        dismissed.value = true
    }
}

private fun Entitlement.trialAvailable() = this is Entitlement.Free && !trialUsed
