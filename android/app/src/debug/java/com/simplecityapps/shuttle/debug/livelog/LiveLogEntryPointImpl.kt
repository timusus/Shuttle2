package com.simplecityapps.shuttle.debug.livelog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.ui.screens.settings.LiveLogEntryPoint
import javax.inject.Inject

/** The debug build's binding for [LiveLogEntryPoint] (see `AppBindsModule`'s `@BindsOptionalOf`). */
class LiveLogEntryPointImpl
@Inject
constructor() : LiveLogEntryPoint {
    @Composable
    override fun Content(onNavigateUp: () -> Unit) {
        val viewModel: LiveLogViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LiveLogScreen(uiState = uiState, onNavigateUp = onNavigateUp, onClear = viewModel::onClear)
    }
}
