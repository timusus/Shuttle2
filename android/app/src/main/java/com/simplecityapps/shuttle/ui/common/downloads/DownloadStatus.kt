package com.simplecityapps.shuttle.ui.common.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.DownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadStatuses
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Provided once for the shell, by `Song.path`; without one (previews, tests) nothing is downloaded. It changes only when
 * a download starts, finishes, fails or goes, so the rows reading it don't redraw on each progress tick.
 */
val LocalDownloadOfflineStates = compositionLocalOf<Map<String, SongOfflineState>> { emptyMap() }

/** The running downloads' progress by path; read by [DownloadStatusHeader] only. */
val LocalDownloadProgress = compositionLocalOf<Map<String, Float>> { emptyMap() }

/** The state of [this] song's download for its row. */
@Composable
fun Song.offlineState(): SongOfflineState = LocalDownloadOfflineStates.current[path] ?: SongOfflineState.None

/** Downloads for the whole app, observed once at the shell. */
@ViewModelKey(DownloadStatusViewModel::class)
@ContributesIntoMap(AppScope::class)
class DownloadStatusViewModel @Inject constructor(
    observeDownloadStatuses: ObserveDownloadStatuses,
) : ViewModel() {
    val uiState: StateFlow<DownloadStatuses> = observeDownloadStatuses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadStatuses())
}

/**
 * Provides [statuses] to [content]'s rows and headers. The row states are read apart from the progress, so a progress
 * tick recomposes only the headers.
 */
@Composable
fun ProvideDownloadStatuses(statuses: State<DownloadStatuses>, content: @Composable () -> Unit) {
    val offline by remember(statuses) {
        derivedStateOf {
            statuses.value.states.mapValues { (_, status) ->
                when (status) {
                    SongDownloadStatus.Downloading -> SongOfflineState.Downloading
                    SongDownloadStatus.Downloaded -> SongOfflineState.Offline
                }
            }
        }
    }
    val progress by remember(statuses) { derivedStateOf { statuses.value.progress } }
    CompositionLocalProvider(LocalDownloadOfflineStates provides offline, LocalDownloadProgress provides progress, content = content)
}

/** How much of a collection of songs is on the device. */
sealed interface CollectionDownload {
    /** Nothing of it is downloaded or downloading, or it has no server songs. */
    data object None : CollectionDownload

    /** [downloaded] of [total] songs are on the device and the rest are on their way: [progress] is the whole's 0..1. */
    data class Downloading(val downloaded: Int, val total: Int, val progress: Float) : CollectionDownload

    /** [downloaded] of [total] songs are on the device and nothing is running (some failed, or were removed). */
    data class Partial(val downloaded: Int, val total: Int) : CollectionDownload

    data object Downloaded : CollectionDownload
}

/** [songs]' downloads, counting only those that come from a server. */
fun collectionDownload(songs: List<Song>, offline: Map<String, SongOfflineState>, progress: Map<String, Float>): CollectionDownload {
    val remote = songs.filter { it.mediaProvider.remote }
    if (remote.isEmpty()) return CollectionDownload.None
    val states = remote.map { offline[it.path] ?: SongOfflineState.None }
    val downloaded = states.count { it == SongOfflineState.Offline }
    val running = remote.filter { offline[it.path] == SongOfflineState.Downloading }
    return when {
        running.isNotEmpty() -> CollectionDownload.Downloading(
            downloaded = downloaded,
            total = remote.size,
            progress = (downloaded + running.sumOf { (progress[it.path] ?: 0f).toDouble() }.toFloat()) / remote.size,
        )

        downloaded == remote.size -> CollectionDownload.Downloaded

        downloaded > 0 -> CollectionDownload.Partial(downloaded, remote.size)

        else -> CollectionDownload.None
    }
}
