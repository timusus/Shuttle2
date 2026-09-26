package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

class AlbumListPreferenceManager(
    private val preferenceManager: GeneralPreferenceManager,
) : AlbumListPreferences {

    /** A grid until the user picks the list: albums are the most visual thing in the library (#491). */
    override var albumListViewMode: ViewMode
        get() = preferenceManager.albumListViewMode?.let(ViewMode::valueOf) ?: ViewMode.Grid
        set(value) {
            preferenceManager.albumListViewMode = value.name
        }
}
