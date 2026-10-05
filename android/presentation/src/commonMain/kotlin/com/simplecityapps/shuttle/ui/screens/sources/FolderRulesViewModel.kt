package com.simplecityapps.shuttle.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FolderRulesUiState(
    val folders: FolderLists = FolderLists(),
    val events: List<PendingEvent<FolderRulesEvent>> = emptyList(),
)

sealed interface FolderRulesEvent {
    /** An exclude needs a folder on this device's storage, which the picked one isn't. */
    data object FolderNotOnDevice : FolderRulesEvent
}

/**
 * Settings > Sources > This device > Folder rules (#379, #881): the S2 scanner's folders. Each change starts a scan, so
 * the library follows the rules straight away.
 */
@ViewModelKey(FolderRulesViewModel::class)
@ContributesIntoMap(AppScope::class)
class FolderRulesViewModel @Inject constructor(
    private val mediaSources: MediaSources,
    observeScannerFolders: ObserveScannerFolders,
    private val addScannerFolder: AddScannerFolder,
    private val removeScannerFolder: RemoveScannerFolder,
    private val refreshScannerFolders: RefreshScannerFolders,
) : ViewModel() {
    private val events = PendingEvents<FolderRulesEvent>()

    val uiState: StateFlow<FolderRulesUiState> =
        combine(observeScannerFolders(), events.flow) { folders, events -> FolderRulesUiState(folders, events) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderRulesUiState())

    /** Re-reads each folder's access, for a grant revoked while the screen was away. */
    fun onResume() = refreshScannerFolders()

    /** The folder picker's result: null when it was cancelled. */
    fun onFolderPicked(kind: FolderKind, treeUri: String?) {
        if (treeUri == null) return
        if (addScannerFolder(kind, treeUri)) {
            mediaSources.scan(foldersChanged = true)
        } else {
            events.post(FolderRulesEvent.FolderNotOnDevice)
        }
    }

    fun onRemoveFolder(kind: FolderKind, folder: SourceFolder) {
        removeScannerFolder(kind, folder)
        mediaSources.scan(foldersChanged = true)
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
