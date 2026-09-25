package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
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
    observeAlbums: ObserveAlbums,
    private val observeSongs: ObserveSongs,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
    private val resolveSongs: ResolveSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    observePlaylists: ObservePlaylists,
    private val sortPreferenceManager: SortPreferences,
    private val viewModePreferenceManager: AlbumListPreferences,
    mediaImportObserver: SongImportStateProvider,
    private val random: Random,
) : ViewModel() {

    private val selectionState = SelectionState<Album>()

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderAlbumList)
    private val _viewMode = MutableStateFlow(viewModePreferenceManager.albumListViewMode)

    // Not persisted: a fresh app process starts with a new shuffle even if Random remains the
    // selected sort order. Only reassigned when the user (re)selects Random, so library
    // re-emissions (scans, play counts) while on this screen don't reshuffle the list.
    private val _randomSeed = MutableStateFlow(random.nextLong())

    val uiState: StateFlow<AlbumListUiState> = combine(
        observeAlbums(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
        combine(
            _viewMode,
            observePlaylists(),
            _randomSeed,
        ) { viewMode, playlists, randomSeed -> Triple(viewMode, playlists, randomSeed) },
    ) { albums, songImportState, selectedAlbums, sortOrder, (viewMode, playlists, randomSeed) ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumListUiState(
                loadingState = AlbumListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                viewMode = viewMode,
                selectedAlbums = selectedAlbums,
                playlists = playlists,
            )
        } else {
            val sortedAlbums = albums.sortedWith(sortOrder.comparator(randomSeed))
            AlbumListUiState(
                albums = sortedAlbums,
                selectedAlbums = selectedAlbums,
                viewMode = viewMode,
                sortOrder = sortOrder,
                playlists = playlists,
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
            val songs = resolveSongs(MediaSelection.Albums(album))
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(album: Album) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Albums(album), EnqueueSongs.Position.End)
            _events.emit(AlbumListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            enqueueSongs(MediaSelection.Albums(selected), EnqueueSongs.Position.End)
            _events.emit(AlbumListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(album: Album) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Albums(album), EnqueueSongs.Position.Next)
            _events.emit(AlbumListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(album: Album) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Albums(album))
        }
    }

    fun onEditTags(album: Album) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Albums(album))
            _events.emit(AlbumListUiEvent.EditTags(songs))
        }
    }

    fun onEditTagsSelected() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = resolveSongs(MediaSelection.Albums(selected))
            _events.emit(AlbumListUiEvent.EditTags(songs))
            selectionState.clear()
        }
    }

    fun onShuffle() {
        viewModelScope.launch {
            val allSongs = observeSongs().firstOrNull().orEmpty()
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
        if (sortOrder == AlbumSortOrder.Random) {
            _randomSeed.value = random.nextLong()
        }
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

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumListUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumListUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }

    fun createPlaylist(name: String, playlistData: PlaylistData) {
        viewModelScope.launch {
            createPlaylistUseCase(name, playlistData.toMediaSelection())
        }
    }
}
