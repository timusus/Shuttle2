package com.simplecityapps.shuttle.debug.livelog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.ui.screens.settings.LiveLogEntryPoint
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** The debug build's binding for [LiveLogEntryPoint] (see `AppBindsModule`'s `@BindsOptionalOf`). */
class LiveLogEntryPointImpl
@Inject
constructor() : LiveLogEntryPoint {
    @Composable
    override fun Content(onNavigateUp: () -> Unit) {
        val viewModel: LiveLogViewModel = metroViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LiveLogScreen(uiState = uiState, onNavigateUp = onNavigateUp, onClear = viewModel::onClear)
    }
}
