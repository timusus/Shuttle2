package com.simplecityapps.shuttle

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.ui.screens.welcome.WelcomeSettings
import dev.zacsweers.metro.Inject

/**
 * Tells a new install from an upgrade, once per launch and before anything reads the settings it seeds. Every launch
 * since 2019 has saved [GeneralPreferenceManager.previousVersionCode], so only a new install (or cleared data) lacks it.
 */
class InstallDefaults @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val privacySettings: PrivacySettings,
    private val analyticsConsentSettings: AnalyticsConsentSettings,
    private val welcomeSettings: WelcomeSettings,
) {
    fun onLaunch(versionCode: Int = BuildConfig.VERSION_CODE, versionName: String = BuildConfig.VERSION_NAME) {
        if (preferenceManager.previousVersionCode == -1) {
            // Fresh installs skip the changelog, which is for upgrades; the welcome notice covers the defaults.
            preferenceManager.lastViewedChangelogVersion = versionName
            welcomeSettings.pending.value = true
        } else if (preferenceManager.previousVersionCode != versionCode) {
            // A dismissed consent card wrote no explicit value, so it still counts as the opt-out it was.
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
