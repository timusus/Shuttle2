package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import javax.inject.Inject

/** Whether the What's new card should show: changelog-on-launch is on and this version's notes haven't been seen. */
class IsWhatsNewPending @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke(): Boolean = preferenceManager.showChangelogOnLaunch && preferenceManager.lastViewedChangelogVersion != BuildConfig.VERSION_NAME
}

/** Marks this version's changelog as seen, so [IsWhatsNewPending] stops flagging it. */
class MarkChangelogViewed @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
) {
    operator fun invoke() {
        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }
}
