package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeSongImportStateProvider : SongImportStateProvider {
    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
    override val songImportState: StateFlow<SongImportState> = _songImportState

    private val _providerImportStates = MutableStateFlow<Map<MediaProviderType, SongImportState>>(emptyMap())
    override val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = _providerImportStates

    /** Reports [state] as the importer does: the latest state, and its provider's own. */
    fun setState(state: SongImportState) {
        _songImportState.value = state
        val type = when (state) {
            SongImportState.Idle -> return
            is SongImportState.ImportProgress -> state.providerType
            is SongImportState.ImportComplete -> state.providerType
        }
        _providerImportStates.update { it + (type to state) }
    }
}

fun importComplete(
    providerType: MediaProviderType = MediaProviderType.Shuttle,
) = SongImportState.ImportComplete(providerType, null)
