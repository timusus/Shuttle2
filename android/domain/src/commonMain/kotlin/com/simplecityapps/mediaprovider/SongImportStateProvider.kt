package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.StateFlow

interface SongImportStateProvider {
    /** What [providerImportStates] come to for the library as a whole ([overallImportState]). */
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

/**
 * What each provider's import state, [states], comes to for the library as a whole, so no one provider's report stands in
 * for another's: in progress while any provider's import runs, saying what the first of them is doing, and how far
 * through all of them are when each one knows; then how they ended, a failure first.
 */
fun overallImportState(states: Map<MediaProviderType, SongImportState>): SongImportState {
    val running = states.values.filterIsInstance<SongImportState.ImportProgress>().sortedBy { it.providerType.ordinal }
    if (running.isNotEmpty()) {
        val progresses = running.map { it.progress ?: return running.first().copy(progress = null) }
        return running.first().copy(progress = Progress(progresses.sumOf { it.progress }, progresses.sumOf { it.total }))
    }
    val complete = states.values.filterIsInstance<SongImportState.ImportComplete>().sortedBy { it.providerType.ordinal }
    return complete.firstOrNull { it.error != null } ?: complete.firstOrNull() ?: SongImportState.Idle
}
