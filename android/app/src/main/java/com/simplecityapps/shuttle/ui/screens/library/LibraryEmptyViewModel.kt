package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccessCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

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
 * Home and Library each create their own instance of this ViewModel, but both forward to the same
 * [MusicAccessCoordinator] (#427), so the permission check and scan-once bookkeeping run once for the app, not once
 * per screen. The screen reports the permission ([onAccessChecked] on every resume, [onAccessResult] after the
 * system prompt), because only the Activity can ask whether a rationale should show.
 */
@HiltViewModel
class LibraryEmptyViewModel @Inject constructor(
    private val musicAccess: MusicAccessCoordinator,
) : ViewModel() {
    val uiState: StateFlow<LibraryAvailability> = musicAccess.availability

    fun onAccessChecked(granted: Boolean, showRationale: Boolean) = musicAccess.onChecked(granted, showRationale)

    fun onAccessResult(granted: Boolean, showRationale: Boolean) = musicAccess.onResult(granted, showRationale)

    /** Scans again from the "No music found" state. */
    fun onScan() = musicAccess.rescan()
}
