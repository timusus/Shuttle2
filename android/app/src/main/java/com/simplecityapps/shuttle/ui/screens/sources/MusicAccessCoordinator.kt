package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Whether the Library has music to show, tracked once for the whole app (#427): Home and Library used to each hold
 * their own [LibraryEmptyViewModel][com.simplecityapps.shuttle.ui.screens.library.LibraryEmptyViewModel] instance,
 * so the music permission and scan-once bookkeeping below ran twice on every resume, once per screen.
 *
 * [onChecked] reports the permission as it stands on resume; [onResult] reports the system prompt's answer. Only
 * the very first report of either kind defers to [MediaSources.scanIfNeverScanned], which covers a permission
 * already held (granted in system settings while backgrounded, or restored with a backup) without rescanning a
 * library that already has one.
 */
@Singleton
class MusicAccessCoordinator @Inject constructor(
    observeSongs: ObserveSongs,
    importState: SongImportStateProvider,
    private val mediaSources: MediaSources,
    private val settings: SourcesSettings,
    @AppCoroutineScope appCoroutineScope: CoroutineScope,
) {
    private val access = MutableStateFlow<MusicAccess?>(null)

    val availability: StateFlow<LibraryAvailability> =
        combine(
            observeSongs().map { songs -> songs.isNotEmpty() }.distinctUntilChanged(),
            access,
            importState.songImportState,
        ) { hasSongs, access, import ->
            when {
                hasSongs -> LibraryAvailability.HasMusic
                access == null -> LibraryAvailability.Loading
                else -> LibraryAvailability.Empty(access, (import as? SongImportState.ImportProgress)?.let { ScanProgress(it.message, it.progress?.asFloat()) })
            }
        }.stateIn(appCoroutineScope, SharingStarted.WhileSubscribed(5_000), LibraryAvailability.Loading)

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
