package com.simplecityapps.shuttle.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import com.simplecityapps.shuttle.ui.screens.settings.ObserveLastScanDate
import com.simplecityapps.shuttle.ui.screens.sources.servers.ForgetServer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A media server in Sources, [connected] when the library imports from it, with its import's [status] and its [songs] once the library has loaded. */
data class ServerSource(
    val type: MediaProviderType,
    val connected: Boolean,
    val status: SourceStatus = SourceStatus.Idle,
    val songs: Int? = null,
    /** When an import from it last completed, if one has. */
    val updated: Instant? = null,
    /** How many songs the server counts but doesn't return on a full listing, if it comes up short. */
    val listingShortfall: Int = 0,
)

data class SourcesUiState(
    /** Whether the library imports this device's music. */
    val thisDevice: Boolean = false,
    /** True for users who chose the Android (MediaStore) provider before #379: it ignores the folder lists. */
    val usesAndroidProvider: Boolean = false,
    val folders: FolderLists = FolderLists(),
    /** Any source's import while one runs; iOS's scan row shows it. */
    val scan: ScanProgress? = null,
    /** The last import's failure message, cleared as soon as another starts; iOS's scan row shows it. */
    val scanError: String? = null,
    /** This device's own import, for its card: [scan] and [scanError] follow whichever source reported last. */
    val deviceStatus: SourceStatus = SourceStatus.Idle,
    /** Songs from this device, once the library has loaded. */
    val deviceSongs: Int? = null,
    /** When an import of this device's music last completed, if one has. */
    val deviceUpdated: Instant? = null,
    /** How many files this device's last full import couldn't read (#840): each crashed a tag read, so it's left unread until a retry. */
    val deviceSkippedFiles: Int = 0,
    val servers: List<ServerSource> = ServerTypes.map { ServerSource(it, connected = false) },
    /** When any import last finished, if one ever has: iOS's scan row shows it. */
    val lastImport: Instant? = null,
)

val ServerTypes = listOf(MediaProviderType.Jellyfin, MediaProviderType.Emby, MediaProviderType.Plex, MediaProviderType.Subsonic)

/**
 * Settings > Sources (#379): this device on or off, a rescan, and the media servers, each
 * source with its import status and song count (#663). Source changes start a scan, so the library follows them
 * straight away; the folder rules have their own [FolderRulesViewModel] (#881).
 */
@ViewModelKey(SourcesViewModel::class)
@ContributesIntoMap(AppScope::class)
class SourcesViewModel @Inject constructor(
    private val mediaSources: MediaSources,
    observeScannerFolders: ObserveScannerFolders,
    private val refreshScannerFolders: RefreshScannerFolders,
    importState: SongImportStateProvider,
    private val tryAddServer: TryAddServer,
    private val connectServer: ConnectServer,
    observeLastScanDate: ObserveLastScanDate,
    private val forgetServer: ForgetServer,
    observeSongCounts: ObserveSongCounts,
    observeSourceReachability: ObserveSourceReachability,
    observeSourceUpdated: ObserveSourceUpdated,
    observeListingShortfalls: ObserveListingShortfalls,
    observeDeviceSkippedFiles: ObserveDeviceSkippedFiles,
    private val clearSkippedFiles: ClearSkippedFiles,
) : ViewModel() {
    private val imports: Flow<Imports> =
        combine(importState.songImportState, importState.providerImportStates, observeSongCounts(), combine(observeSourceReachability(), observeSourceUpdated(), observeListingShortfalls(), observeDeviceSkippedFiles(), ::Stored), ::Imports)

    val uiState: StateFlow<SourcesUiState> =
        combine(mediaSources.enabledTypes, observeScannerFolders(), imports, observeLastScanDate()) { types, folders, imports, lastImport ->
            val latest = imports.latest
            SourcesUiState(
                thisDevice = types.any { it.isLocal },
                usesAndroidProvider = MediaProviderType.MediaStore in types,
                folders = folders,
                scan = (latest as? SongImportState.ImportProgress)?.let { ScanProgress(it.message, it.progress?.asFloat()) },
                scanError = (latest as? SongImportState.ImportComplete)?.error,
                deviceStatus = sourceStatus(types.firstOrNull { it.isLocal }?.let(imports.byProvider::get)),
                deviceSongs = imports.songCounts?.filterKeys { it.isLocal }?.values?.sum(),
                deviceUpdated = imports.updated.filterKeys { it.isLocal }.values.filterNotNull().maxOrNull(),
                deviceSkippedFiles = imports.stored.deviceSkippedFiles,
                servers = ServerTypes.map { type ->
                    ServerSource(type, connected = type in types, status = sourceStatus(imports.byProvider[type], imports.reachability[type]), songs = imports.songCounts?.let { it[type] ?: 0 }, updated = imports.updated[type], listingShortfall = imports.shortfalls[type] ?: 0)
                },
                lastImport = lastImport,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SourcesUiState())

    /** Re-reads the folders' access, which the This device card flags when a grant was revoked. */
    fun onResume() = refreshScannerFolders()

    fun onThisDeviceChange(enabled: Boolean) {
        if (enabled) {
            mediaSources.enable(MediaProviderType.Shuttle)
            mediaSources.scan()
        } else {
            mediaSources.enabledTypes.value.filter { it.isLocal }.forEach(mediaSources::disable)
        }
    }

    fun onRescan() = mediaSources.scan()

    /** Reads the files this device's import left unread again, in a rescan. */
    fun onRetrySkippedFiles() {
        clearSkippedFiles()
        mediaSources.scan()
    }

    /** Whether a server's sign-in may open: once the trial is over without Pro, the gate opens the paywall instead. */
    fun onAddServer(): Boolean = tryAddServer()

    /** A server's sign-in dialog succeeded. */
    fun onServerConnected(type: MediaProviderType) = connectServer(type)

    /** Stops importing from [type]'s server, drops its music, and forgets its address and sign-in (#645). */
    fun onRemoveServer(type: MediaProviderType) {
        mediaSources.disable(type)
        forgetServer(type)
    }

    /** The importer's latest state, each provider's own, the library's songs per provider, how each server's last import ended, when each source last updated, and each server's listing shortfall. */
    private data class Imports(
        val latest: SongImportState,
        val byProvider: Map<MediaProviderType, SongImportState>,
        val songCounts: Map<MediaProviderType, Int>?,
        val stored: Stored,
    ) {
        val reachability get() = stored.reachability
        val updated get() = stored.updated
        val shortfalls get() = stored.shortfalls
    }

    /** What each source's imports left in preferences: how the last one ended, when one last completed, the server's listing shortfall, and the files this device's left unread. */
    private data class Stored(
        val reachability: Map<MediaProviderType, SourceReachability?>,
        val updated: Map<MediaProviderType, Instant?>,
        val shortfalls: Map<MediaProviderType, Int>,
        val deviceSkippedFiles: Int,
    )
}
