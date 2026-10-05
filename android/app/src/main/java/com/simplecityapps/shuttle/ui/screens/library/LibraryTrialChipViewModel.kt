package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The chip stays out of the Library top bar until the trial's last [TRIAL_CHIP_DAYS] days. */
const val TRIAL_CHIP_DAYS = 3

/** Days left to show on the Library's trial chip, or null where it shouldn't show: no trial running, or more than [TRIAL_CHIP_DAYS] days left. */
fun Entitlement.trialChipDaysLeft(now: Instant = Clock.System.now()): Int? = (this as? Entitlement.Trial)
    ?.daysRemaining(now)
    ?.takeIf { it in 1..TRIAL_CHIP_DAYS }

/** The Library top bar's trial chip: the days left in the last days of the server trial, from the resolved [Entitlement]. */
@ViewModelKey(LibraryTrialChipViewModel::class)
@ContributesIntoMap(AppScope::class)
class LibraryTrialChipViewModel @Inject constructor(
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>
) : ViewModel() {
    /** The days left to show on the chip, or null to hide it ([trialChipDaysLeft]). */
    val uiState: StateFlow<Int?> = entitlement
        .map { it.trialChipDaysLeft() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), entitlement.value.trialChipDaysLeft())
}
