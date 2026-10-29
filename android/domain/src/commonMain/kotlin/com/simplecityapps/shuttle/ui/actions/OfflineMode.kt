package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** The persisted Offline mode switch, implemented over the app's settings. */
interface OfflineModeSource {
    fun observe(): Flow<Boolean>

    fun set(enabled: Boolean)
}

/**
 * A local song always plays; a server song plays offline only once its download has completed. A merged song plays
 * while any of its copies does, as its best copy then ranks first.
 */
fun Song.isAvailableOffline(downloadedPaths: Set<String>): Boolean = !mediaProvider.remote || path in downloadedPaths ||
    copies.any { copy -> !copy.mediaProvider.remote || copy.path in downloadedPaths }

/** The paths of the completed downloads; progress ticks don't re-emit it. */
fun DownloadStatusSource.observeDownloadedPaths(): Flow<Set<String>> = observe()
    .map { statuses -> statuses.states.filterValues { it == SongDownloadStatus.Downloaded }.keys }
    .distinctUntilChanged()

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
            combine(songs, downloadStatuses.observeDownloadedPaths()) { list, paths -> list.filter { it.isAvailableOffline(paths) } }
        } else {
            songs
        }
    }
}

/**
 * Narrows songs about to be played or queued: with Offline mode on, the server songs that aren't downloaded can't play
 * and are dropped. Reads the switch and downloads once, so selections for other actions (delete, favourite) resolve whole.
 */
@Inject
class FilterPlayableSongs(
    private val offlineMode: OfflineModeSource,
    private val downloadStatuses: DownloadStatusSource,
) {
    suspend operator fun invoke(songs: List<Song>): List<Song> {
        if (songs.isEmpty() || !offlineMode.observe().first()) return songs
        val paths = downloadStatuses.observeDownloadedPaths().first()
        return songs.filter { it.isAvailableOffline(paths) }
    }
}
