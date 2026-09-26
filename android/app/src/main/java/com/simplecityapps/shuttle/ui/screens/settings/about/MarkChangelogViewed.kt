package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

/** Records that this version's changelog has been seen. */
class MarkChangelogViewed @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke() {
        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }
}
