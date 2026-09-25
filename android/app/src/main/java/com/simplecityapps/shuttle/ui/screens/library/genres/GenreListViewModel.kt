package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenreListUiState(
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
    val sortOrder: GenreSortOrder = GenreSortOrder.Default,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
}

sealed interface GenreListUiEvent {
    data class AddedToQueue(val genreName: String) : GenreListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : GenreListUiEvent
    data class EditTags(val songs: List<Song>) : GenreListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : GenreListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : GenreListUiEvent
    data class PlaylistAddFailed(val message: String?) : GenreListUiEvent
}

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val playSongs: PlaySongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
    private val resolveSongs: ResolveSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val playlistRepository: PlaylistRepository,
    private val sortPreferenceManager: SortPreferences,
    mediaImportObserver: SongImportStateProvider
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderGenreList)

    val uiState: StateFlow<GenreListUiState> = combine(
        genreRepository.getGenres(GenreQuery.All()),
        mediaImportObserver.songImportState,
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
        _sortOrder,
    ) { genres, songImportState, playlists, sortOrder ->
        if (songImportState is SongImportState.ImportProgress) {
            GenreListUiState(
                loadingState = GenreListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                playlists = playlists,
                sortOrder = sortOrder,
            )
        } else {
            val sortedGenres = genres.sortedWith(sortOrder.comparator)
            GenreListUiState(
                genres = sortedGenres,
                playlists = playlists,
                sortOrder = sortOrder,
                loadingState = if (sortedGenres.isEmpty()) {
                    GenreListUiState.LoadingState.Empty
                } else {
                    GenreListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GenreListUiState(),
    )

    private val _events = MutableSharedFlow<GenreListUiEvent>()
    val events: SharedFlow<GenreListUiEvent> = _events.asSharedFlow()

    fun onPlay(genre: Genre) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Genres(genre))
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(GenreListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(genre: Genre) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Genres(genre), EnqueueSongs.Position.End)
            _events.emit(GenreListUiEvent.AddedToQueue(genre.name))
        }
    }

    fun onPlayNext(genre: Genre) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Genres(genre), EnqueueSongs.Position.Next)
            _events.emit(GenreListUiEvent.AddedToQueue(genre.name))
        }
    }

    fun onExclude(genre: Genre) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Genres(genre))
        }
    }

    fun onEditTags(genre: Genre) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Genres(genre))
            _events.emit(GenreListUiEvent.EditTags(songs))
        }
    }

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(GenreListUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        GenreListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(GenreListUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }

    fun createPlaylist(name: String, playlistData: PlaylistData) {
        viewModelScope.launch {
            createPlaylistUseCase(name, playlistData.toMediaSelection())
        }
    }

    fun setSortOrder(sortOrder: GenreSortOrder) {
        sortPreferenceManager.sortOrderGenreList = sortOrder
        _sortOrder.value = sortOrder
    }
}
