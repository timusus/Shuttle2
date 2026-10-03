package com.simplecityapps.shuttle.ui.screens.settings

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveLogGateUiState(val entryPoint: LiveLogEntryPoint? = null)

/**
 * Resolves the debug-only [LiveLogEntryPoint] via an optional binding, so this file never imports a
 * class that only exists in the debug build. Absent in release, where the row that opens this route is also
 * hidden (see [com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog]).
 */
@ViewModelKey(LiveLogGateViewModel::class)
@ContributesIntoMap(AppScope::class)
class LiveLogGateViewModel @Inject constructor(
    entryPoint: Optional<LiveLogEntryPoint> = Optional.empty()
) : ViewModel() {
    val uiState: StateFlow<LiveLogGateUiState> = MutableStateFlow(LiveLogGateUiState(entryPoint.orElse(null))).asStateFlow()
}
