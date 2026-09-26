package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

/** One album's songs and header, loaded by [groupKey], the key its route carries. */
@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumGroupKey?,
    private val observeSongs: ObserveSongs,
    private val observeAlbums: ObserveAlbums,
    private val queueOperations: QueueOperations,
    private val observePlaylists: ObservePlaylists,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumGroupKey?): AlbumDetailViewModel
    }

    private val currentSong: Flow<Song?> = queueOperations.queueStateFlow
        .map { queueState -> queueState.currentItem?.song }
        .distinctUntilChanged()

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        observeSongs(SongQuery.AlbumGroupKey(key = groupKey)),
        observeAlbums(AlbumQuery.AlbumGroupKey(groupKey)),
        currentSong,
        observePlaylists(),
    ) { songs, albums, currentSong, playlists ->
        AlbumDetailUiState(
            album = albums.firstOrNull(),
            songs = songs,
            playlists = playlists,
            currentSong = currentSong,
            loadingState = if (songs.isEmpty()) {
                AlbumDetailUiState.LoadingState.Empty
            } else {
                AlbumDetailUiState.LoadingState.Ready
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )
}
