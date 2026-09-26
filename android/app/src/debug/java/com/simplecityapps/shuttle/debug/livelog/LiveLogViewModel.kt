package com.simplecityapps.shuttle.debug.livelog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LiveLogUiState(val lines: List<LiveLogLine> = emptyList())

/** Backs the Live log screen: [LiveLogBuffer]'s recent output, live. */
@HiltViewModel
class LiveLogViewModel
@Inject
constructor(
    private val buffer: LiveLogBuffer
) : ViewModel() {
    val uiState: StateFlow<LiveLogUiState> = buffer.lines
        .map { LiveLogUiState(lines = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveLogUiState(lines = buffer.lines.value))

    fun onClear() = buffer.clear()
}
