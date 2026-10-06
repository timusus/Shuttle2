package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** iOS has no offline downloads yet, so no song ever has a download state. */
@ContributesBinding(AppScope::class)
class IosDownloadStatusSource @Inject constructor() : DownloadStatusSource {
    override fun observe(): Flow<DownloadStatuses> = flowOf(DownloadStatuses())
}
