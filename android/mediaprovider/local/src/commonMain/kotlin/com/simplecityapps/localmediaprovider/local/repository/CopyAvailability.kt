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
import kotlinx.coroutines.flow.map

/**
 * What [LibraryCopies] ranks copies by: Offline mode, the completed downloads and the servers whose last check failed.
 * A server counts as unreachable until a later import of it succeeds.
 */
fun copyAvailability(
    offlineMode: OfflineModeSource,
    downloads: DownloadStatusSource,
    sourceState: SourceStateStore
): Flow<SongCopyAvailability> {
    val unreachable = combine(
        MediaProviderType.entries.filter { it.remote }.map { type ->
            sourceState.observeSourceReachability(type.name).map { reachability -> type.takeIf { reachability?.error != null } }
        }
    ) { types -> types.filterNotNull().toSet() }
    return combine(offlineMode.observe(), downloads.observeDownloadedPaths(), unreachable) { offline, paths, down ->
        SongCopyAvailability(offline = offline, downloadedPaths = paths, unreachableProviders = down)
    }.distinctUntilChanged()
}
