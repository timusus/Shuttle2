package com.simplecityapps.shuttle.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import com.simplecityapps.trial.ServerAccessGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A media server in Sources, [connected] when the library imports from it. */
data class ServerSource(val type: MediaProviderType, val connected: Boolean)

data class SourcesUiState(
    /** Whether the library imports this device's music. */
    val thisDevice: Boolean = false,
    /** True for users who chose the Android (MediaStore) provider before #379: it ignores the folder lists. */
    val usesAndroidProvider: Boolean = false,
    val folders: FolderLists = FolderLists(),
    val scan: ScanProgress? = null,
    /** The last scan's failure message, cleared as soon as another scan starts. */
    val scanError: String? = null,
    val servers: List<ServerSource> = ServerTypes.map { ServerSource(it, connected = false) },
    val events: List<PendingEvent<SourcesEvent>> = emptyList(),
)

sealed interface SourcesEvent {
    /** An exclude needs a folder on this device's storage, which the picked one isn't. */
    data object FolderNotOnDevice : SourcesEvent
}

val ServerTypes = listOf(MediaProviderType.Jellyfin, MediaProviderType.Emby, MediaProviderType.Plex)

/**
 * Settings > Sources (#379): this device on or off, the S2 scanner's folders, a rescan, and the media servers. Folder
 * and source changes start a scan, so the library follows them straight away.
 */
@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val mediaSources: MediaSources,
    observeScannerFolders: ObserveScannerFolders,
    private val addScannerFolder: AddScannerFolder,
    private val removeScannerFolder: RemoveScannerFolder,
    private val refreshScannerFolders: RefreshScannerFolders,
    importState: SongImportStateProvider,
    private val serverAccessGate: ServerAccessGate,
) : ViewModel() {
    private val events = PendingEvents<SourcesEvent>()

    val uiState: StateFlow<SourcesUiState> =
        combine(mediaSources.enabledTypes, observeScannerFolders(), importState.songImportState, events.flow) { types, folders, import, events ->
            SourcesUiState(
                thisDevice = types.any { it.isLocal },
                usesAndroidProvider = MediaProviderType.MediaStore in types,
                folders = folders,
                scan = (import as? SongImportState.ImportProgress)?.let { ScanProgress(it.message, it.progress?.asFloat()) },
                scanError = (import as? SongImportState.ImportComplete)?.error,
                servers = ServerTypes.map { ServerSource(it, connected = it in types) },
                events = events,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())

    fun onResume() = refreshScannerFolders()

    fun onThisDeviceChange(enabled: Boolean) {
        if (enabled) {
            mediaSources.enable(MediaProviderType.Shuttle)
            mediaSources.scan()
        } else {
            mediaSources.enabledTypes.value.filter { it.isLocal }.forEach(mediaSources::disable)
        }
    }

    /** The folder picker's result: null when it was cancelled. */
    fun onFolderPicked(kind: FolderKind, treeUri: String?) {
        if (treeUri == null) return
        if (addScannerFolder(kind, treeUri)) {
            mediaSources.scan()
        } else {
            events.post(SourcesEvent.FolderNotOnDevice)
        }
    }

    fun onRemoveFolder(kind: FolderKind, folder: SourceFolder) {
        removeScannerFolder(kind, folder)
        mediaSources.scan()
    }

    fun onRescan() = mediaSources.scan()

    /** Whether a server's sign-in may open: once the trial is over without Pro, the gate opens the paywall instead. */
    fun onAddServer(): Boolean = serverAccessGate.tryAddServer()

    /** A server's sign-in dialog succeeded. */
    fun onServerConnected(type: MediaProviderType) {
        mediaSources.enable(type)
        mediaSources.scan()
    }

    fun onRemoveServer(type: MediaProviderType) = mediaSources.disable(type)

    fun onEventHandled(id: Long) = events.consume(id)
}
