package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [MediaImporter] publishes of its progress: each source's import state, the overall state they come to, and the imports completed. */
internal class ImportStateTracker(
    private val strings: MediaImportStrings
) {
    /** Held while [_providerImportStates] and [_songImportState] change, so the overall state is always that of the latest states. */
    private val stateLock = Mutex()

    private val _songImportState = MutableStateFlow<SongImportState>(SongImportState.Idle)

    /** The running import's progress, or how the last one ended, so a collector that arrives mid-import sees where it's at. */
    val songImportState: StateFlow<SongImportState> = _songImportState.asStateFlow()

    private val _providerImportStates = MutableStateFlow<Map<MediaProviderType, SongImportState>>(emptyMap())

    val providerImportStates: StateFlow<Map<MediaProviderType, SongImportState>> = _providerImportStates.asStateFlow()

    private val _importsCompleted = MutableStateFlow(0)

    val importsCompleted: StateFlow<Int> = _importsCompleted.asStateFlow()

    fun stateOf(type: MediaProviderType): SongImportState? = _providerImportStates.value[type]

    suspend fun publish(
        type: MediaProviderType,
        state: SongImportState
    ) = updateStates { states -> states + (type to state) }

    /** Reports how [type]'s import ended, [error] if it failed, and counts it in [importsCompleted]. */
    suspend fun complete(
        type: MediaProviderType,
        error: String?
    ) = updateStates(completed = true) { states -> states + (type to SongImportState.ImportComplete(type, error)) }

    suspend fun remove(type: MediaProviderType) = updateStates { states -> states - type }

    /** Swaps in [transform] of each provider's state, and the overall state they come to ([overallImportState]), counting one more of [importsCompleted] if [completed]. */
    suspend fun updateStates(
        completed: Boolean = false,
        transform: (Map<MediaProviderType, SongImportState>) -> Map<MediaProviderType, SongImportState>
    ) {
        stateLock.withLock {
            val states = transform(_providerImportStates.value)
            _providerImportStates.value = states
            _songImportState.value = overallImportState(states)
            if (completed) _importsCompleted.value++
        }
    }

    /** [progress] as the import state shows it: described in the user's words, with its count. */
    fun MediaProvider.importProgress(progress: MessageProgress) = SongImportState.ImportProgress(type, strings.describe(progress, provider = type.name), progress.progress)
}
