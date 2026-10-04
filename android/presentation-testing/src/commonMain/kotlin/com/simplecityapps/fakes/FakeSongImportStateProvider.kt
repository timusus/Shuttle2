package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.overallImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSongImportStateProvider : SongImportStateProvider {
    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)
    override val songImportState: StateFlow<SongImportState> = _songImportState

    private val _providerImportStates = MutableStateFlow<Map<MediaProviderType, SongImportState>>(emptyMap())
    override val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = _providerImportStates

    private val _importsCompleted = MutableStateFlow(0)
    override val importsCompleted: StateFlow<Int> = _importsCompleted

    /**
     * Reports [state] as the importer does: as its provider's own, and the overall state they come to (Idle forgets them all),
     * counting an [SongImportState.ImportComplete] in [importsCompleted].
     */
    fun setState(state: SongImportState) {
        val states = when (state) {
            SongImportState.Idle -> emptyMap()
            is SongImportState.ImportProgress -> _providerImportStates.value + (state.providerType to state)
            is SongImportState.ImportComplete -> _providerImportStates.value + (state.providerType to state)
        }
        _providerImportStates.value = states
        _songImportState.value = overallImportState(states)
        if (state is SongImportState.ImportComplete) _importsCompleted.value++
    }
}

fun importComplete(
    providerType: MediaProviderType = MediaProviderType.Shuttle,
) = SongImportState.ImportComplete(providerType, null)
