package com.simplecityapps.shuttle.ui.actions

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

/** Where a song's download stands, for the songs with one worth showing. */
enum class SongDownloadStatus { Downloading, Downloaded }

/**
 * Every song's download status by `Song.path`, the running downloads' progress (0..1) by the same, and the bytes each
 * download holds on the device by the same.
 */
data class DownloadStatuses(
    val states: Map<String, SongDownloadStatus> = emptyMap(),
    val progress: Map<String, Float> = emptyMap(),
    val sizes: Map<String, Long> = emptyMap(),
) {
    /** The storage all downloads use, in bytes. */
    val storageBytes: Long get() = sizes.values.sum()
}

/** The app's download state, implemented over its download manager. */
interface DownloadStatusSource {
    fun observe(): Flow<DownloadStatuses>
}

/** The statuses of every download, as they change. */
@Inject
class ObserveDownloadStatuses(
    private val source: DownloadStatusSource,
) {
    operator fun invoke(): Flow<DownloadStatuses> = source.observe()
}
