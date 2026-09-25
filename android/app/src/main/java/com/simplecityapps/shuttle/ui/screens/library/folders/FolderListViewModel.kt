package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.FolderNode
import com.simplecityapps.shuttle.model.FolderTree
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A folder row. [path] identifies the folder (see [com.simplecityapps.shuttle.model.SongFolder]); its first
 * segment is the storage volume.
 */
data class Folder(
    val path: List<String>,
    val songCount: Int,
) {
    val name: String get() = path.last()
}

data class FolderListUiState(
    /** The folder being browsed, or null at the top level. */
    val currentFolder: Folder? = null,
    val folders: List<Folder> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val canNavigateUp: Boolean get() = currentFolder != null
}

sealed interface FolderListUiEvent {
    data class FolderAddedToQueue(val folder: Folder) : FolderListUiEvent
    data class SongAddedToQueue(val song: Song) : FolderListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : FolderListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : FolderListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : FolderListUiEvent
    data class PlaylistAddFailed(val message: String?) : FolderListUiEvent
    data object DeleteFailed : FolderListUiEvent
}

@HiltViewModel
class FolderListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val resolveFolderSongs: ResolveFolderSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    private val playlistRepository: PlaylistRepository,
    private val savedStateHandle: SavedStateHandle,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    /** Derived once per library change, so navigating between folders doesn't rebuild it. */
    private val folderTree: StateFlow<FolderTree?> = songRepository
        .getSongs(SongQuery.All())
        .filterNotNull()
        .map { songs -> FolderTree.build(songs) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The browsed folder's path; null means the top level. */
    private val currentPath: StateFlow<List<String>?> = savedStateHandle.getStateFlow(KEY_PATH, null)

    val uiState: StateFlow<FolderListUiState> = combine(
        folderTree,
        currentPath,
        mediaImportObserver.songImportState,
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
    ) { tree, path, songImportState, playlists ->
        when {
            songImportState is SongImportState.ImportProgress -> FolderListUiState(
                loadingState = FolderListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                playlists = playlists,
            )

            tree == null -> FolderListUiState(playlists = playlists)

            tree.isEmpty -> FolderListUiState(
                playlists = playlists,
                loadingState = FolderListUiState.LoadingState.Empty,
            )

            else -> {
                // A folder can disappear after a rescan; fall back to its nearest remaining ancestor
                val node = path
                    ?.let { tree.root.nearest(it) }
                    ?.takeIf { it.path.size > tree.displayRoot.path.size }
                    ?: tree.displayRoot
                FolderListUiState(
                    currentFolder = node.takeIf { it !== tree.displayRoot }?.toFolder(),
                    folders = node.subfolders.map { it.toFolder() },
                    songs = node.songs,
                    playlists = playlists,
                    loadingState = FolderListUiState.LoadingState.Ready,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FolderListUiState(),
    )

    private val _events = MutableSharedFlow<FolderListUiEvent>()
    val events: SharedFlow<FolderListUiEvent> = _events.asSharedFlow()

    // Navigation

    fun onFolderClick(folder: Folder) {
        savedStateHandle[KEY_PATH] = ArrayList(folder.path)
    }

    fun onNavigateUp() {
        val current = uiState.value.currentFolder ?: return
        // Paths at or above the top level are normalised back to it when state is derived
        savedStateHandle[KEY_PATH] = ArrayList(current.path.dropLast(1))
    }

    // Folder actions

    fun onPlay(folder: Folder) {
        viewModelScope.launch {
            val result = playSongs(resolveFolderSongs(listOf(folder.path)))
            if (result is PlaySongs.Result.Failure) {
                _events.emit(FolderListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onShuffle(folder: Folder) {
        viewModelScope.launch {
            val result = shuffleSongs(resolveFolderSongs(listOf(folder.path)))
            if (result is ShuffleSongs.Result.Failure) {
                _events.emit(FolderListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(folder: Folder) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Folders(listOf(folder.path)), EnqueueSongs.Position.End)
            _events.emit(FolderListUiEvent.FolderAddedToQueue(folder))
        }
    }

    fun onPlayNext(folder: Folder) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Folders(listOf(folder.path)), EnqueueSongs.Position.Next)
            _events.emit(FolderListUiEvent.FolderAddedToQueue(folder))
        }
    }

    // Song actions

    fun onSongClick(song: Song) {
        viewModelScope.launch {
            val songs = uiState.value.songs.ifEmpty { listOf(song) }
            val result = playSongs(songs, position = songs.indexOf(song).coerceAtLeast(0))
            if (result is PlaySongs.Result.Failure) {
                _events.emit(FolderListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.End)
            _events.emit(FolderListUiEvent.SongAddedToQueue(song))
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.Next)
            _events.emit(FolderListUiEvent.SongAddedToQueue(song))
        }
    }

    fun onExclude(song: Song) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Songs(song))
        }
    }

    fun onDelete(song: Song) {
        viewModelScope.launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) _events.emit(FolderListUiEvent.DeleteFailed)
        }
    }

    // Playlists

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(FolderListUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        FolderListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(FolderListUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }

    fun createPlaylist(name: String, playlistData: PlaylistData) {
        viewModelScope.launch {
            createPlaylistUseCase(name, playlistData.toMediaSelection())
        }
    }

    private fun FolderNode.toFolder() = Folder(path = path, songCount = songCount)

    companion object {
        private const val KEY_PATH = "folder_path"
    }
}
