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
         * Sentry crash reporting, applied at startup and whenever it changes (TelemetryConsentGate), with the opt-out
         * in Settings > Privacy. On by default for everyone who hasn't explicitly turned it off (owner decision,
         * #481); an explicit off always stays off.
         */
        val CrashReporting = Setting.boolean("pref_crash_reporting", true)

        /**
         * PostHog product analytics, applied at startup and whenever it changes (TelemetryConsentGate), with the
         * opt-out in Settings > Privacy. On by default for everyone who hasn't explicitly turned it off (owner
         * decision, #481); an explicit off always stays off, including from the deleted Home consent card's No
         * thanks or dismiss (#421), migrated to an explicit off by InstallDefaults. The key predates PostHog: it
         * keeps the choice users made for Firebase Analytics.
         */
        val Analytics = Setting.boolean("pref_firebase_analytics", true)
    }
}
