package com.simplecityapps.shuttle.ui.screens.welcome

import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The first run's welcome (#1040). */
@SingleIn(AppScope::class)
class WelcomeSettings @Inject constructor(
    store: SettingsStore
) {
    val pending = store.preference(Pending)

    companion object {
        /**
         * Whether the welcome still has to show. Only InstallDefaults sets it, on a new install, so an upgrade (which
         * never stores it) never sees the welcome.
         */
        val Pending = Setting.boolean("welcome_pending", false)
    }
}
