package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.StartTrial
import com.simplecityapps.shuttle.entitlement.TrialOfferDismissal
import com.simplecityapps.shuttle.entitlement.TrialStart
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
    private val startFailed = MutableStateFlow(false)

    val uiState: StateFlow<TrialOfferUiState> = combine(entitlement, dismissed, startFailed) { entitlement, dismissed, startFailed ->
        TrialOfferUiState(offered = entitlement.trialAvailable() && !dismissed, startFailed = startFailed)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TrialOfferUiState(offered = entitlement.value.trialAvailable() && !dismissed.value, startFailed = false)
    )

    fun onStartTrial() {
        viewModelScope.launch {
            startFailed.value = false
            startFailed.value = startTrial() == TrialStart.Unavailable
        }
    }

    fun onDismiss() {
        dismissal.dismiss()
        dismissed.value = true
    }
}

/** [offered]: the card shows. [startFailed]: Play didn't answer in time, so the last tap started nothing; the next tap retries. */
data class TrialOfferUiState(
    val offered: Boolean,
    val startFailed: Boolean
)

private fun Entitlement.trialAvailable() = this is Entitlement.Free && !trialUsed
