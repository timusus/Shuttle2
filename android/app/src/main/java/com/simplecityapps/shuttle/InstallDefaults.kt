package com.simplecityapps.shuttle

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import javax.inject.Inject

/**
 * Tells a new install from an upgrade, once per launch and before anything reads the settings it seeds. Every launch
 * since 2019 has saved [GeneralPreferenceManager.previousVersionCode], so only a new install (or cleared data) lacks it.
 */
class InstallDefaults @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val privacySettings: PrivacySettings,
    private val analyticsConsentSettings: AnalyticsConsentSettings,
) {
    fun onLaunch(versionCode: Int = BuildConfig.VERSION_CODE, versionName: String = BuildConfig.VERSION_NAME) {
        if (preferenceManager.previousVersionCode == -1) {
            // Nothing to catch up on: the changelog is for upgrades. Crash reporting and analytics default to on
            // (#379, #481) on their own; nothing silently changed for a new install, so it never needs the notice.
            preferenceManager.lastViewedChangelogVersion = versionName
            analyticsConsentSettings.noticeShown.value = true
        } else if (preferenceManager.previousVersionCode != versionCode) {
            // First launch after upgrading to this version: analytics (and crash reporting) now default to on for
            // anyone who never chose (#481). The deleted Home consent card's No thanks or dismiss (#421) never wrote
            // an explicit value, so treat it as the opt-out it was.
            if (analyticsConsentSettings.asked.value && !privacySettings.analytics.isSet()) {
                privacySettings.analytics.value = false
            }
            // Anyone with an explicit choice by now, including that migration, made it already: no notice needed.
            if (privacySettings.analytics.isSet()) {
                analyticsConsentSettings.noticeShown.value = true
            }
        }
        if (preferenceManager.previousVersionCode != versionCode) {
            preferenceManager.previousVersionCode = versionCode
        }
    }
}
