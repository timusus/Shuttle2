package com.simplecityapps.shuttle.ui.screens.settings.about

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.platform.AppVersion
import dev.zacsweers.metro.Inject

/** Whether the What's new card should show: changelog-on-launch is on and this version's notes haven't been seen. */
class IsWhatsNewPending @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val appVersion: AppVersion,
) {
    operator fun invoke(): Boolean = preferenceManager.showChangelogOnLaunch && preferenceManager.lastViewedChangelogVersion != appVersion.name()
}

/** Marks this version's changelog as seen, so [IsWhatsNewPending] stops flagging it. */
class MarkChangelogViewed @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val appVersion: AppVersion,
) {
    operator fun invoke() {
        preferenceManager.lastViewedChangelogVersion = appVersion.name()
    }
}
