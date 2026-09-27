package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AlbumListPreferenceManager @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) : AlbumListPreferences {

    /** A grid until the user picks the list: albums are the most visual thing in the library (#491). */
    override var albumListViewMode: ViewMode
        get() = preferenceManager.albumListViewMode?.let(ViewMode::valueOf) ?: ViewMode.Grid
        set(value) {
            preferenceManager.albumListViewMode = value.name
        }
}
