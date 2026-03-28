package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode

class AlbumListPreferenceManager(
    private val preferenceManager: GeneralPreferenceManager,
) : AlbumListPreferences {

    override var albumListViewMode: ViewMode
        get() = preferenceManager.albumListViewMode.toViewMode()
        set(value) {
            preferenceManager.albumListViewMode = value.name
        }
}
