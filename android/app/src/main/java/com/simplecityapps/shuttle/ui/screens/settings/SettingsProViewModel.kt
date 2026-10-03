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

/** What the Settings root's Shuttle Music Pro row shows, from the resolved [Entitlement]. */
enum class SettingsProState {
    /** Play hasn't answered yet, so a purchaser can't be told apart from a new user: just the title, no copy to flash. */
    Neutral,

    /** No Pro (the trial counts: it ends in the upsell). */
    Upsell,

    /** Pro is owned. */
    Owned
}

/** The Settings root's Shuttle Music Pro row, from the resolved [Entitlement]. */
@ViewModelKey(SettingsProViewModel::class)
@ContributesIntoMap(AppScope::class)
class SettingsProViewModel @Inject constructor(
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>
) : ViewModel() {
    val proState: StateFlow<SettingsProState> = entitlement
        .map { it.toProState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), entitlement.value.toProState())

    private fun Entitlement.toProState(): SettingsProState = when (this) {
        Entitlement.Unknown -> SettingsProState.Neutral
        is Entitlement.Free, is Entitlement.Trial -> SettingsProState.Upsell
        is Entitlement.Pro -> SettingsProState.Owned
    }
}
