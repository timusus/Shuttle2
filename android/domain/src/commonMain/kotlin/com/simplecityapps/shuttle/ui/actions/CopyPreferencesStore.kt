package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.SongCopyPreferences
import kotlinx.coroutines.flow.Flow

/** The copy settings as the shared layer sees them, implemented over the app's settings. */
interface CopyPreferencesStore {
    val mergeCopies: Flow<Boolean>
    val preferences: Flow<SongCopyPreferences>

    /** Stores what [change] makes of the current primary copies and albums; the preferred copy stays a setting row's. */
    fun update(change: (SongCopyPreferences) -> SongCopyPreferences)
}
