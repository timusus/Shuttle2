package com.simplecityapps.shuttle.settings

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class PrivacySettings @Inject constructor(
    store: SettingsStore
) {
    val crashReporting = store.preference(CrashReporting)
    val analytics = store.preference(Analytics)

    companion object {
        /** Crash reporting is on for anyone who never chose; an explicit off always stays off. */
        val CrashReporting = Setting.boolean("pref_crash_reporting", true)

        /** Analytics is on for anyone who never chose; an explicit off always stays off. The key keeps Firebase's stored choice. */
        val Analytics = Setting.boolean("pref_firebase_analytics", true)
    }
}
