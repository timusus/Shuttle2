package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.OfflineModeSource
import com.simplecityapps.shuttle.ui.actions.observeDownloadedPaths
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What [LibraryCopies] ranks copies by: Offline mode, the completed downloads, the servers whose last check failed and
 * the streaming quality's cap. A server counts as unreachable, and a song that failed to play as missing, until a later
 * import of its source succeeds.
 */
fun copyAvailability(
    offlineMode: OfflineModeSource,
    downloads: DownloadStatusSource,
    sourceState: SourceStateStore,
    streamingCapKbps: Flow<Int?> = flowOf(null)
): Flow<SongCopyAvailability> {
    val unreachable = combine(
        MediaProviderType.entries.filter { it.remote }.map { type ->
            sourceState.observeSourceReachability(type.name).map { reachability -> type.takeIf { reachability?.error != null } }
        }
    ) { types -> types.filterNotNull().toSet() }
    val missing = combine(MediaProviderType.entries.map { type -> sourceState.observeMissingSongs(type.name) }) { sets -> sets.flatMap { it }.toSet() }
    return combine(offlineMode.observe(), downloads.observeDownloadedPaths(), unreachable, missing, streamingCapKbps) { offline, paths, down, missingIds, cap ->
        SongCopyAvailability(offline = offline, downloadedPaths = paths, unreachableProviders = down, missingSongIds = missingIds, streamingCapKbps = cap)
    }.distinctUntilChanged()
}
