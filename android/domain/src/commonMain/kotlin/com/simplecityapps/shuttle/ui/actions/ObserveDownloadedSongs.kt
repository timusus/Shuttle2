package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/** The songs whose download has completed, whatever Offline mode says; re-emits as downloads and the library change. */
@Inject
class ObserveDownloadedSongs(
    private val songRepository: SongRepository,
    private val downloadStatuses: DownloadStatusSource,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<Song>> = downloadStatuses.observeDownloadedPaths()
        .flatMapLatest { paths -> songRepository.getSongs(SongQuery.Downloaded(paths)).filterNotNull() }
}
