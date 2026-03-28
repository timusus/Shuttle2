package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSongImportStateProvider : SongImportStateProvider {
    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
    override val songImportState: StateFlow<SongImportState> = _songImportState

    fun setState(state: SongImportState) {
        _songImportState.value = state
    }
}
