package com.simplecityapps.shuttle.ui.screens.onboarding

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject

/** Whether the first-run source setup has been closed, finished or skipped. */
class IsSourceSetupCompleted @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) {
    operator fun invoke(): Boolean = preferences.sourceSetupCompleted
}

/** Records that the source setup was closed, finished or skipped, so it doesn't open by itself again. */
class CompleteSourceSetup @Inject constructor(
    private val preferences: GeneralPreferenceManager,
) {
    operator fun invoke() {
        preferences.sourceSetupCompleted = true
    }
}
