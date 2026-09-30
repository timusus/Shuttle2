package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
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

/** [state] as a source's status: a finished import with no error is [SourceStatus.Idle] again. */
internal fun sourceStatus(state: SongImportState?): SourceStatus = when (state) {
    is SongImportState.ImportProgress -> SourceStatus.Importing(state.progress)
    is SongImportState.ImportComplete -> state.error?.let(SourceStatus::Failed) ?: SourceStatus.Idle
    SongImportState.Idle, null -> SourceStatus.Idle
}

/** How many songs the library holds from each source, as it changes; null until the library has loaded. */
class ObserveSongCounts @Inject constructor(
    private val songRepository: SongRepository,
) {
    operator fun invoke(): Flow<Map<MediaProviderType, Int>?> = songRepository.getSongs(SongQuery.All()).map { songs -> songs?.groupingBy { it.mediaProvider }?.eachCount() }
}
