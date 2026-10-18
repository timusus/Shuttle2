package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.persistence.SourceStateStore
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** How a source's import is going, for its card in Sources. */
sealed interface SourceStatus {
    /** Not importing, and its last import this session (if any) went through. */
    data object Idle : SourceStatus

    /** Importing: [progress] songs of its total, once the source knows how many there are. */
    data class Importing(val progress: Progress? = null) : SourceStatus

    /** Its last import failed, for a server usually because it couldn't be reached. */
    data class Failed(val error: String) : SourceStatus
}

/** Before this session imports from a source, [stored] (kept across restarts) stands in for [state]. */
internal fun sourceStatus(state: SongImportState?, stored: SourceReachability? = null): SourceStatus = when (state) {
    is SongImportState.ImportProgress -> SourceStatus.Importing(state.progress)
    is SongImportState.ImportComplete -> state.error?.let(SourceStatus::Failed) ?: SourceStatus.Idle
    SongImportState.Idle, null -> stored?.error?.let(SourceStatus::Failed) ?: SourceStatus.Idle
}

/** When each source's import last completed successfully, as it changes. */
class ObserveSourceUpdated @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Instant?>> = combine(MediaProviderType.entries.map { type -> sourceState.observeSourceUpdated(type.name) }) { updated ->
        MediaProviderType.entries.zip(updated.toList()).toMap()
    }
}

/** How many songs each server counts but doesn't return on a full listing. */
class ObserveListingShortfalls @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Int>> = combine(ServerTypes.map { type -> sourceState.observeListingShortfall(type.name) }) { shortfalls ->
        ServerTypes.zip(shortfalls.toList()).toMap()
    }
}

/** How many files this device's last full import left unread. */
class ObserveDeviceSkippedFiles @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke(): Flow<Int> = combine(LocalTypes.map { type -> sourceState.observeSkippedFiles(type.name) }) { counts -> counts.sum() }
}

/** Lets the next import read again the files that crashed a tag read, and clears their count. */
class ClearSkippedFiles @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke() {
        sourceState.clearTagReadQuarantine()
        LocalTypes.forEach { type -> sourceState.setSkippedFiles(type.name, 0) }
    }
}

private val LocalTypes = MediaProviderType.entries.filter { it.isLocal }

/** How each server's last import ended, as it changes. */
class ObserveSourceReachability @Inject constructor(
    private val sourceState: SourceStateStore
) {
    operator fun invoke(): Flow<Map<MediaProviderType, SourceReachability?>> = combine(ServerTypes.map { type -> sourceState.observeSourceReachability(type.name) }) { reachabilities ->
        ServerTypes.zip(reachabilities.toList()).toMap()
    }
}

/** How many songs the library holds from each source, as it changes; null until the library has loaded. */
class ObserveSongCounts @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Int>?> = songRepository.countSongsByProvider().onStart<Map<MediaProviderType, Int>?> { emit(null) }
}
