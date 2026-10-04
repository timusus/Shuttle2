package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** How a source's import is going, for its card in Sources (#663). */
sealed interface SourceStatus {
    /** Not importing, and its last import this session (if any) went through. */
    data object Idle : SourceStatus

    /** Importing: [progress] songs of its total, once the source knows how many there are. */
    data class Importing(val progress: Progress? = null) : SourceStatus

    /** Its last import failed, for a server usually because it couldn't be reached. */
    data class Failed(val error: String) : SourceStatus
}

/**
 * [state] as a source's status: a finished import with no error is [SourceStatus.Idle] again. Before this session has
 * imported from the source, [stored] (how its last import ended, kept across restarts) stands in (#668).
 */
internal fun sourceStatus(state: SongImportState?, stored: SourceReachability? = null): SourceStatus = when (state) {
    is SongImportState.ImportProgress -> SourceStatus.Importing(state.progress)
    is SongImportState.ImportComplete -> state.error?.let(SourceStatus::Failed) ?: SourceStatus.Idle
    SongImportState.Idle, null -> stored?.error?.let(SourceStatus::Failed) ?: SourceStatus.Idle
}

/** When each source's import last completed successfully, now and each time one does (#668). */
class ObserveSourceUpdated @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Instant?>> = combine(MediaProviderType.entries.map { type -> generalPreferenceManager.observeSourceUpdated(type.name) }) { updated ->
        MediaProviderType.entries.zip(updated.toList()).toMap()
    }
}

/** How many songs each server counts but doesn't return, now and each time a full listing changes it (#868). */
class ObserveListingShortfalls @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Int>> = combine(ServerTypes.map { type -> generalPreferenceManager.observeListingShortfall(type.name) }) { shortfalls ->
        ServerTypes.zip(shortfalls.toList()).toMap()
    }
}

/** How many files this device's last full import couldn't read and left unread (#840), now and each time one ends. */
class ObserveDeviceSkippedFiles @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Int> = combine(LocalTypes.map { type -> generalPreferenceManager.observeSkippedFiles(type.name) }) { counts -> counts.sum() }
}

/** Lets the next import read again the files that crashed a tag read (#840), and clears the count Sources shows until it has. */
class ClearSkippedFiles @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke() {
        generalPreferenceManager.clearTagReadQuarantine()
        LocalTypes.forEach { type -> generalPreferenceManager.setSkippedFiles(type.name, 0) }
    }
}

private val LocalTypes = MediaProviderType.entries.filter { it.isLocal }

/** How each server's last import ended, now and each time one ends (#668). */
class ObserveSourceReachability @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Map<MediaProviderType, SourceReachability?>> = combine(ServerTypes.map { type -> generalPreferenceManager.observeSourceReachability(type.name) }) { reachabilities ->
        ServerTypes.zip(reachabilities.toList()).toMap()
    }
}

/** How many songs the library holds from each source, as it changes; null until the library has loaded. */
class ObserveSongCounts @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Int>?> = songRepository.getSongs(SongQuery.All()).map { songs -> songs?.groupingBy { it.mediaProvider }?.eachCount() }
}
