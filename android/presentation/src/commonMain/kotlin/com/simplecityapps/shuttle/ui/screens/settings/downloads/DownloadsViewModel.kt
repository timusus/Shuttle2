package com.simplecityapps.shuttle.ui.screens.settings.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadStatuses
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.SongDownloadStatus
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An album with songs on the device: [cover] is one of its downloaded songs, which names the artwork to show. */
data class DownloadedAlbum(
    val key: String,
    val name: String?,
    val artist: String?,
    val songCount: Int,
    val bytes: Long,
    val cover: Song
)

/** [storageBytes] is everything downloads hold, including songs still downloading or no longer in the library. */
data class DownloadsUiState(
    val albums: List<DownloadedAlbum> = emptyList(),
    val storageBytes: Long = 0,
    val loading: Boolean = true
)

/** The albums downloaded for offline playback, the storage they use, and a way to remove them all. */
@ViewModelKey(DownloadsViewModel::class)
@ContributesIntoMap(AppScope::class)
class DownloadsViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    observeDownloadStatuses: ObserveDownloadStatuses,
    private val songDownloader: SongDownloader
) : ViewModel() {
    val uiState: StateFlow<DownloadsUiState> = combine(observeSongs(), observeDownloadStatuses()) { songs, statuses ->
        val albums = songs
            .filter { statuses.states[it.path] == SongDownloadStatus.Downloaded }
            .groupBy { it.albumGroupKey }
            .map { (groupKey, albumSongs) ->
                val cover = albumSongs.first()
                DownloadedAlbum(
                    key = groupKey.encode(),
                    name = cover.album,
                    artist = cover.friendlyArtistName,
                    songCount = albumSongs.size,
                    bytes = albumSongs.sumOf { statuses.sizes[it.path] ?: 0L },
                    cover = cover
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
        DownloadsUiState(albums = albums, storageBytes = statuses.storageBytes, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun onRemoveAll() {
        viewModelScope.launch {
            MediaProviderType.entries.filter { it.remote }.forEach { songDownloader.removeAll(it) }
        }
    }
}
