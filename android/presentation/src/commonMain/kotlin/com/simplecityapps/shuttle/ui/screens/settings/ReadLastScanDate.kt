package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/** When the library was last scanned, if it has been. */
class ReadLastScanDate @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Instant? = generalPreferenceManager.lastMediaImportDate
}

/** When the library was last scanned, now and each time an import finishes (#648). */
class ObserveLastScanDate @Inject constructor(
    private val generalPreferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Instant?> = generalPreferenceManager.observeLastMediaImportDate()
}
