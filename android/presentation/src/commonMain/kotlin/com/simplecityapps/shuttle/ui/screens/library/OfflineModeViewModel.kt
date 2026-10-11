package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.ui.actions.ObserveOfflineMode
import com.simplecityapps.shuttle.ui.actions.SetOfflineMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The Library top bar's Offline mode switch. */
@ViewModelKey(OfflineModeViewModel::class)
@ContributesIntoMap(AppScope::class)
class OfflineModeViewModel @Inject constructor(
    observeOfflineMode: ObserveOfflineMode,
    private val setOfflineMode: SetOfflineMode,
) : ViewModel() {
    val uiState: StateFlow<Boolean> = observeOfflineMode().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggle() = setOfflineMode(!uiState.value)
}
