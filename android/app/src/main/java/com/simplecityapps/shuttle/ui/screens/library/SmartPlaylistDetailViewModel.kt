package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SmartPlaylistDetailUiState(
    val smartPlaylist: SmartPlaylist? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
)

/** One of the built-in smart playlists, resolved from its [SmartPlaylistId] slug; its songs in the playlist's own sort. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SmartPlaylistDetailViewModel.Factory::class)
class SmartPlaylistDetailViewModel @AssistedInject constructor(
    @Assisted smartPlaylistId: String,
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
    queueManager: QueueOperations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(smartPlaylistId: String): SmartPlaylistDetailViewModel
    }

    private val id = SmartPlaylistId.fromId(smartPlaylistId)

    private val smartPlaylist = playlistRepository.getSmartPlaylists().map { playlists -> playlists.firstOrNull { SmartPlaylistId.of(it) == id && id != null } }

    private val songs = smartPlaylist
        .distinctUntilChanged()
        .flatMapLatest { playlist ->
            playlist?.let { songRepository.getSongs(it.songQuery).filterNotNull().map { songs -> songs.sortedWith(it.songQuery.sortOrder.comparator) } }
                ?: flowOf(emptyList())
        }

    val uiState: StateFlow<SmartPlaylistDetailUiState> = combine(
        smartPlaylist,
        songs,
        queueManager.queueStateFlow.map { it.currentItem?.song }.distinctUntilChanged(),
    ) { playlist, songs, currentSong ->
        SmartPlaylistDetailUiState(smartPlaylist = playlist, songs = songs, currentSong = currentSong, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartPlaylistDetailUiState())
}
