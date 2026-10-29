package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.ui.actions.CopyAvailabilitySource
import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.OfflineModeSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

// The streaming cap only reorders copies, never changes whether one plays, so it stays out of what screens explain.
@ContributesBinding(AppScope::class)
class LibraryCopyAvailabilitySource @Inject constructor(
    private val offlineMode: OfflineModeSource,
    private val downloads: DownloadStatusSource,
    private val sourceState: SourceStateStore,
) : CopyAvailabilitySource {
    override fun observe(): Flow<SongCopyAvailability> = copyAvailability(offlineMode, downloads, sourceState)
}
