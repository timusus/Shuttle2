package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** An import in progress: the provider's latest message ("Artist • Song") and how far through it is, if it knows. */
data class ScanProgress(val message: String? = null, val fraction: Float? = null)

/** Whether the Library has music to show, and if not, what stands in the way. */
sealed interface LibraryAvailability {
    data object Loading : LibraryAvailability

    data object HasMusic : LibraryAvailability

    /** No songs yet: [access] decides what the empty state offers, and [scan] is the import running, if any. */
    data class Empty(val access: MusicAccess, val scan: ScanProgress? = null) : LibraryAvailability
}

/**
 * First run without onboarding screens (#379): the Library opens straight away and, while it has no songs, asks for
 * the music permission, starts the scan once it's granted and shows the scan's progress.
 *
 * The screen reports the permission ([onAccessChecked] on every resume, [onAccessResult] after the system prompt),
 * because only the Activity can ask whether a rationale should show.
 */
@HiltViewModel
class LibraryEmptyViewModel @Inject constructor(
    songRepository: SongRepository,
    importState: SongImportStateProvider,
    private val mediaSources: MediaSources,
    private val settings: SourcesSettings,
) : ViewModel() {
    private val access = MutableStateFlow<MusicAccess?>(null)

    val uiState: StateFlow<LibraryAvailability> =
        combine(
            songRepository.getSongs(SongQuery.All()).map { songs -> songs?.isNotEmpty() }.distinctUntilChanged(),
            access,
            importState.songImportState,
        ) { hasSongs, access, import ->
            when {
                hasSongs == null -> LibraryAvailability.Loading
                hasSongs -> LibraryAvailability.HasMusic
                access == null -> LibraryAvailability.Loading
                else -> LibraryAvailability.Empty(access, (import as? SongImportState.ImportProgress)?.let { ScanProgress(it.message, it.progress?.asFloat()) })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryAvailability.Loading)

    /**
     * The permission as it stands on resume. One granted in the system settings meanwhile starts the scan the prompt
     * would have. The very first check of a fresh instance defers to [MediaSources.scanIfNeverScanned], which covers
     * permission already held (granted in system settings while backgrounded, before this screen ever opened) without
     * rescanning a library that already has one.
     */
    fun onAccessChecked(granted: Boolean, showRationale: Boolean) {
        val previous = access.value
        update(granted, showRationale)
        when {
            previous == null -> mediaSources.scanIfNeverScanned(granted)
            granted && previous != MusicAccess.Granted -> scan()
        }
    }

    /** The system prompt's answer. */
    fun onAccessResult(granted: Boolean, showRationale: Boolean) {
        settings.musicPermissionRequested.value = true
        val previous = access.value
        update(granted, showRationale)
        if (granted && previous != MusicAccess.Granted) scan()
    }

    /** Scans again from the "No music found" state. */
    fun onScan() = scan()

    private fun update(granted: Boolean, showRationale: Boolean) {
        access.value = MusicAccess.of(granted, settings.musicPermissionRequested.value, showRationale)
    }

    private fun scan() = mediaSources.scanThisDevice()
}
