package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.toViewMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class ArtistListPreferenceManager @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) : ArtistListPreferences {

    override var artistListViewMode: ViewMode
        get() = preferenceManager.artistListViewMode.toViewMode()
        set(value) {
            preferenceManager.artistListViewMode = value.name
        }
}
