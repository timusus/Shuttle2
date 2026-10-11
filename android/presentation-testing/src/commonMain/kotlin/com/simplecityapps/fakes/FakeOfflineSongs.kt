package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.ui.actions.DownloadStatusSource
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.FilterOfflineGroups
import com.simplecityapps.shuttle.ui.actions.FilterOfflineSongs
import com.simplecityapps.shuttle.ui.actions.OfflineModeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The edges of [FilterOfflineSongs] as flows to drive: Offline mode (off by default) and the download statuses. */
class FakeOfflineSongs {
    val offlineMode = MutableStateFlow(false)
    val statuses = MutableStateFlow(DownloadStatuses())

    val modeSource = object : OfflineModeSource {
        override fun observe(): Flow<Boolean> = offlineMode

        override fun set(enabled: Boolean) {
            offlineMode.value = enabled
        }
    }

    val statusSource = object : DownloadStatusSource {
        override fun observe(): Flow<DownloadStatuses> = statuses
    }

    val filter = FilterOfflineSongs(modeSource, statusSource)

    fun groups(songRepository: SongRepository) = FilterOfflineGroups(modeSource, statusSource, songRepository)
}
