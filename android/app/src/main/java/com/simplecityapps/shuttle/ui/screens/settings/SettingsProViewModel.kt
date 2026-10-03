package com.simplecityapps.shuttle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.entitlement.Entitlement
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the user owns Shuttle Music Pro, for the Settings root's Pro row. */
@ViewModelKey(SettingsProViewModel::class)
@ContributesIntoMap(AppScope::class)
class SettingsProViewModel @Inject constructor(
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>
) : ViewModel() {
    val isPro: StateFlow<Boolean> = entitlement
        .map { it is Entitlement.Pro }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), entitlement.value is Entitlement.Pro)
}
