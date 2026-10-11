package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/** The persisted Offline mode switch, implemented over the app's settings. */
interface OfflineModeSource {
    fun observe(): Flow<Boolean>

    fun set(enabled: Boolean)
}

/** Whether Offline mode is on, as it changes. */
@Inject
class ObserveOfflineMode(
    private val source: OfflineModeSource,
) {
    operator fun invoke(): Flow<Boolean> = source.observe()
}

@Inject
class SetOfflineMode(
    private val source: OfflineModeSource,
) {
    operator fun invoke(enabled: Boolean) = source.set(enabled)
}

/** A local song always plays; a server song plays offline only once its download has completed. */
fun Song.isAvailableOffline(statuses: DownloadStatuses): Boolean = !mediaProvider.remote || statuses.states[path] == SongDownloadStatus.Downloaded

/**
 * The one place Offline mode narrows a song list: with it on, server songs that aren't fully downloaded are dropped
 * and local songs are untouched. With it off the songs pass through and the download state isn't read.
 */
@Inject
class FilterOfflineSongs(
    private val offlineMode: OfflineModeSource,
    private val downloadStatuses: DownloadStatusSource,
) {
    operator fun invoke(songs: Flow<List<Song>>): Flow<List<Song>> = offlineMode.observe().distinctUntilChanged().flatMapLatest { offline ->
        if (offline) {
            combine(songs, downloadStatuses.observe()) { list, statuses -> list.filter { it.isAvailableOffline(statuses) } }
        } else {
            songs
        }
    }
}
