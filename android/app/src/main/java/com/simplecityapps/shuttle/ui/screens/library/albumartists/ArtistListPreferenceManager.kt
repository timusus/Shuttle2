package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode

class ArtistListPreferenceManager(
    private val preferenceManager: GeneralPreferenceManager,
) : ArtistListPreferences {

    override var artistListViewMode: ViewMode
        get() = preferenceManager.artistListViewMode.toViewMode()
        set(value) {
            preferenceManager.artistListViewMode = value.name
        }
}
