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
         * Sentry crash reporting, applied at startup and whenever it changes (TelemetryConsentGate), with the opt-out in
         * Settings > Privacy. A new install stores it on (owner decision 3, #379; see InstallDefaults); this default,
         * off, is what existing users who never chose have always had.
         */
        val CrashReporting = Setting.boolean("pref_crash_reporting", false)

        /**
         * PostHog product analytics, applied at startup and whenever it changes (TelemetryConsentGate), with the
         * opt-out in Settings > Privacy. A new install stores it on (owner decision, #481; see InstallDefaults); this
         * default, off, is what existing users who never chose have always had — the Home consent card (#421) offers
         * them the same choice. The key predates PostHog: it keeps the choice users made for Firebase Analytics.
         */
        val Analytics = Setting.boolean("pref_firebase_analytics", false)
    }
}
