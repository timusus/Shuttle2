package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.model.SongCopyPreferences
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What decides whether a copy can play right now, as the library ranks copies by it. */
interface CopyAvailabilitySource {
    fun observe(): Flow<SongCopyAvailability>
}

/** Everything that decides which copy of a merged song plays and why, for screens that explain it. */
data class CopyContext(
    val mergeCopies: Boolean = true,
    val preferences: SongCopyPreferences = SongCopyPreferences(),
    val availability: SongCopyAvailability = SongCopyAvailability(),
)

@Inject
class ObserveCopyContext(
    private val store: CopyPreferencesStore,
    private val availability: CopyAvailabilitySource,
) {
    operator fun invoke(): Flow<CopyContext> = combine(store.mergeCopies, store.preferences, availability.observe(), ::CopyContext)
}
