package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * Tracks music access once for the whole app, so Home and Library don't each run the permission and scan bookkeeping.
 * [onChecked] reports the permission on resume; [onResult] reports the system prompt's answer.
 */
@SingleIn(AppScope::class)
class MusicAccessCoordinator @Inject constructor(
    observeSongs: ObserveSongs,
    importState: SongImportStateProvider,
    private val mediaSources: MediaSources,
    private val settings: SourcesSettings,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) {
    private val access = MutableStateFlow<MusicAccess?>(null)

    /**
     * Which servers' first imports are still to end. A server joins when it is enabled (at launch, only if nothing has
     * ever been scanned) and leaves when its import ends however it ends: completes, fails or is cancelled back to idle.
     * An import that never starts stays pending until the server is disabled. Held for the process, not for the
     * subscribers of [availability]: a cancelled import never marks the library scanned, so refolding on resubscribe
     * would put the server back to pending with nothing running. `enabledTypes` is a StateFlow, so the launch state is
     * read synchronously and there is no window before it loads.
     */
    private val firstImports: StateFlow<FirstImports> = run {
        val launchServers = mediaSources.enabledTypes.value.filterNot { it.isLocal }.toSet()
        val launch = FirstImports(pending = launchServers.takeUnless { mediaSources.hasScanned }.orEmpty(), known = launchServers)
        combine(mediaSources.enabledTypes, importState.providerImportStates) { enabled, states ->
            Triple(enabled.filterNot { it.isLocal }.toSet(), states.filterValues { it is SongImportState.ImportProgress }.keys, states.filterValues { it is SongImportState.ImportComplete }.keys)
        }.runningFold(launch) { previous, (servers, running, complete) ->
            val ended = (previous.started - running) + complete
            FirstImports(
                pending = (previous.pending + (servers - previous.known)).intersect(servers) - ended,
                started = (previous.started + running).intersect(servers) - ended,
                known = servers,
            )
        }.stateIn(appCoroutineScope, SharingStarted.Eagerly, launch)
    }

    val availability: StateFlow<LibraryAvailability> =
        combine(
            observeSongs(includeUnavailable = true).map { songs -> songs.isNotEmpty() }.distinctUntilChanged(),
            access,
            importState.songImportState,
            mediaSources.enabledTypes,
            firstImports,
        ) { hasSongs, access, import, enabled, firstImports ->
            val hasServer = enabled.any { !it.isLocal }
            val awaitingFirstImport = firstImports.pending.isNotEmpty()
            when {
                hasSongs -> LibraryAvailability.HasMusic

                access == null -> LibraryAvailability.Loading

                else -> LibraryAvailability.Empty(
                    access,
                    (import as? SongImportState.ImportProgress)?.let { ScanProgress(it.message, it.progress?.asFloat()) }
                        ?: ScanProgress().takeIf { awaitingFirstImport },
                    hasServer,
                )
            }
        }.stateIn(appCoroutineScope, SharingStarted.WhileSubscribed(5_000), LibraryAvailability.Loading)

    private data class FirstImports(
        val pending: Set<MediaProviderType> = emptySet(),
        val started: Set<MediaProviderType> = emptySet(),
        val known: Set<MediaProviderType> = emptySet(),
    )

    fun onChecked(granted: Boolean, showRationale: Boolean) {
        val previous = access.value
        update(granted, showRationale)
        when {
            previous == null -> mediaSources.scanIfNeverScanned(granted)
            granted && previous != MusicAccess.Granted -> mediaSources.scanThisDevice()
        }
    }

    fun onResult(granted: Boolean, showRationale: Boolean) {
        settings.musicPermissionRequested.value = true
        val previous = access.value
        update(granted, showRationale)
        if (granted && previous != MusicAccess.Granted) mediaSources.scanThisDevice()
    }

    /** Scans again from the "No music found" state. */
    fun rescan() = mediaSources.scanThisDevice()

    private fun update(granted: Boolean, showRationale: Boolean) {
        access.value = MusicAccess.of(granted, settings.musicPermissionRequested.value, showRationale)
    }
}
