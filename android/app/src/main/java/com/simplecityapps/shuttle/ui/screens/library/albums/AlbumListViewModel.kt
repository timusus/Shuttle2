package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val sortPreferenceManager: SortPreferences,
    private val viewModePreferenceManager: AlbumListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<Album>()

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderAlbumList)
    private val _viewMode = MutableStateFlow(viewModePreferenceManager.albumListViewMode)

    val uiState: StateFlow<AlbumListUiState> = combine(
        albumRepository.getAlbums(AlbumQuery.All()),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
        _viewMode,
    ) { albums, songImportState, selectedAlbums, sortOrder, viewMode ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumListUiState(
                loadingState = AlbumListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                viewMode = viewMode,
                selectedAlbums = selectedAlbums,
            )
        } else {
            val sortedAlbums = albums.sortedWith(sortOrder.comparator)
            AlbumListUiState(
                albums = sortedAlbums,
                selectedAlbums = selectedAlbums,
                viewMode = viewMode,
                sortOrder = sortOrder,
                loadingState = if (sortedAlbums.isEmpty()) {
                    AlbumListUiState.LoadingState.Empty
                } else {
                    AlbumListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumListUiState(),
    )

    private val _events = MutableSharedFlow<AlbumListUiEvent>()
    val events: SharedFlow<AlbumListUiEvent> = _events.asSharedFlow()

    fun onAlbumClick(album: Album) {
        selectionState.toggle(album)
    }

    fun onAlbumLongClick(album: Album) {
        selectionState.toggle(album)
    }

    fun onPlay(album: Album) {
        viewModelScope.launch {
            val songs = getSongsForAlbum(album)
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(album: Album) {
        viewModelScope.launch {
            val songs = getSongsForAlbum(album)
            playbackManager.addToQueue(songs)
            _events.emit(AlbumListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = getSongsForAlbums(selected)
            playbackManager.addToQueue(songs)
            _events.emit(AlbumListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(album: Album) {
        viewModelScope.launch {
            val songs = getSongsForAlbum(album)
            playbackManager.playNext(songs)
            _events.emit(AlbumListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(album: Album) {
        viewModelScope.launch {
            val songs = getSongsForAlbum(album)
            songRepository.setExcluded(songs, true)
        }
    }

    fun onEditTags(album: Album) {
        viewModelScope.launch {
            val songs = getSongsForAlbum(album)
            _events.emit(AlbumListUiEvent.EditTags(songs))
        }
    }

    fun onEditTagsSelected() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = getSongsForAlbums(selected)
            _events.emit(AlbumListUiEvent.EditTags(songs))
            selectionState.clear()
        }
    }

    fun onShuffle() {
        viewModelScope.launch {
            val allSongs = songRepository.getSongs(SongQuery.All()).firstOrNull().orEmpty()
            val shuffledByAlbum = allSongs
                .groupBy { it.album }
                .keys.shuffled()
                .flatMap { albumName -> allSongs.filter { it.album == albumName } }
            val result = shuffleSongs(shuffledByAlbum)
            if (result is ShuffleSongs.Result.Failure) {
                _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun setSortOrder(sortOrder: AlbumSortOrder) {
        sortPreferenceManager.sortOrderAlbumList = sortOrder
        _sortOrder.value = sortOrder
    }

    fun setViewMode(mode: ViewMode) {
        viewModePreferenceManager.albumListViewMode = mode
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }

    fun selectedAlbums(): List<Album> = selectionState.selectedItems.value.toList()

    private suspend fun getSongsForAlbum(album: Album): List<Song> = songRepository
        .getSongs(SongQuery.AlbumGroupKeys(listOf(SongQuery.AlbumGroupKey(album.groupKey))))
        .firstOrNull()
        .orEmpty()

    private suspend fun getSongsForAlbums(albums: List<Album>): List<Song> = songRepository
        .getSongs(SongQuery.AlbumGroupKeys(albums.map { SongQuery.AlbumGroupKey(it.groupKey) }))
        .firstOrNull()
        .orEmpty()
}
