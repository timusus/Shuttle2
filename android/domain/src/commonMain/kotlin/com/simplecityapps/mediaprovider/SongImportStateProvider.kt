package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.StateFlow

interface SongImportStateProvider {
    val songImportState: StateFlow<SongImportState>
}

sealed class SongImportState {
    data object Idle : SongImportState()

    /** [providerType]'s import is running: what it's doing, in words to show the user, and how far through it is if it knows. */
    data class ImportProgress(
        val providerType: MediaProviderType,
        val message: String?,
        val progress: Progress?
    ) : SongImportState()

    data class ImportComplete(val providerType: MediaProviderType, val error: String?) : SongImportState()
}
