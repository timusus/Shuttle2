package com.simplecityapps.shuttle.settings

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacySettings @Inject constructor(
    store: SettingsStore
) {
    val crashReporting = store.preference(CrashReporting)
    val analytics = store.preference(Analytics)

    companion object {
        /** Read once at startup, so a change applies after a restart. The redesign wants this on by default (owner decision 3). */
        val CrashReporting = Setting.boolean("pref_crash_reporting", false)

        /**
         * Firebase Analytics. Remote Config (trial length, pricing tier, snowfall) is only fetched while this is on,
         * so it changes trial and pricing behaviour too.
         */
        val Analytics = Setting.boolean("pref_firebase_analytics", false)
    }
}
