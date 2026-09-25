package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.FolderNode
import com.simplecityapps.shuttle.model.FolderTree
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val canNavigateUp: Boolean get() = currentFolder != null
}

@HiltViewModel
class FolderListViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    private val savedStateHandle: SavedStateHandle,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    /** Derived once per library change, so navigating between folders doesn't rebuild it. */
    private val folderTree: StateFlow<FolderTree?> = observeSongs()
        .map { songs -> FolderTree.build(songs) }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The browsed folder's path; null means the top level. */
    private val currentPath: StateFlow<List<String>?> = savedStateHandle.getStateFlow(KEY_PATH, null)

    val uiState: StateFlow<FolderListUiState> = combine(
        folderTree,
        currentPath,
        mediaImportObserver.songImportState,
    ) { tree, path, songImportState ->
        when {
            songImportState is SongImportState.ImportProgress -> FolderListUiState(
                loadingState = FolderListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
            )

            tree == null -> FolderListUiState()

            tree.isEmpty -> FolderListUiState(loadingState = FolderListUiState.LoadingState.Empty)

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
                    loadingState = FolderListUiState.LoadingState.Ready,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FolderListUiState(),
    )

    // Navigation

    fun onFolderClick(folder: Folder) {
        savedStateHandle[KEY_PATH] = ArrayList(folder.path)
    }

    fun onNavigateUp() {
        val current = uiState.value.currentFolder ?: return
        // Paths at or above the top level are normalised back to it when state is derived
        savedStateHandle[KEY_PATH] = ArrayList(current.path.dropLast(1))
    }

    private fun FolderNode.toFolder() = Folder(path = path, songCount = songCount)

    companion object {
        private const val KEY_PATH = "folder_path"
    }
}
