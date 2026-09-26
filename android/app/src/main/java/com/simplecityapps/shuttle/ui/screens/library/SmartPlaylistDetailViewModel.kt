package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
@HiltViewModel(assistedFactory = SmartPlaylistDetailViewModel.Factory::class)
class SmartPlaylistDetailViewModel @AssistedInject constructor(
    @Assisted smartPlaylistId: String,
    observeSongs: ObserveSongs,
    queueOperations: QueueOperations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(smartPlaylistId: String): SmartPlaylistDetailViewModel
    }

    private val smartPlaylist = SmartPlaylistId.fromId(smartPlaylistId)?.smartPlaylist

    private val songs = smartPlaylist
        ?.let { playlist -> observeSongs(playlist.songQuery).map { songs -> songs.sortedWith(playlist.songQuery.sortOrder.comparator) } }
        ?: flowOf(emptyList())

    val uiState: StateFlow<SmartPlaylistDetailUiState> = combine(
        songs,
        queueOperations.queueStateFlow.map { it.currentItem?.song }.distinctUntilChanged(),
    ) { songs, currentSong ->
        SmartPlaylistDetailUiState(smartPlaylist = smartPlaylist, songs = songs, currentSong = currentSong, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartPlaylistDetailUiState())
}
