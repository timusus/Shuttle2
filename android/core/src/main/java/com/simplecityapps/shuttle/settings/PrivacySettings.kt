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
        /**
         * Read once at startup, so a change applies after a restart, with the opt-out in Settings > Privacy. A new
         * install stores it on (owner decision 3, #379; see InstallDefaults); this default, off, is what existing users
         * who never chose have always had.
         */
        val CrashReporting = Setting.boolean("pref_crash_reporting", false)

        /**
         * Firebase Analytics. Off until the user turns it on; when, if ever, to ask is an open legal question. Remote Config (trial length, pricing tier, snowfall) is only fetched while this is on,
         * so it changes trial and pricing behaviour too.
         */
        val Analytics = Setting.boolean("pref_firebase_analytics", false)
    }
}
