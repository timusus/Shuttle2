package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.StateFlow

interface SongImportStateProvider {
    /** Whichever provider reported last: providers import side by side, so this alone can't say how each one is doing. */
    val songImportState: StateFlow<SongImportState>

    /** Each provider's own import: its progress while it runs, then how it ended, until its next import starts. Empty until one has run. */
    val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>>
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
