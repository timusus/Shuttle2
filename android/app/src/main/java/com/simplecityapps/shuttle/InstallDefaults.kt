package com.simplecityapps.shuttle

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.PrivacySettings
import javax.inject.Inject

/**
 * Tells a new install from an upgrade, once per launch and before anything reads the settings it seeds. Every launch
 * since 2019 has saved [GeneralPreferenceManager.previousVersionCode], so only a new install (or cleared data) lacks it.
 */
class InstallDefaults @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager,
    private val privacySettings: PrivacySettings,
) {
    fun onLaunch(versionCode: Int = BuildConfig.VERSION_CODE, versionName: String = BuildConfig.VERSION_NAME) {
        if (preferenceManager.previousVersionCode == -1) {
            // Nothing to catch up on: the changelog is for upgrades
            preferenceManager.lastViewedChangelogVersion = versionName
            // On for new installs (#379, #481). Existing users who never chose keep the old default, off
            privacySettings.crashReporting.value = true
            privacySettings.analytics.value = true
        }
        if (preferenceManager.previousVersionCode != versionCode) {
            preferenceManager.previousVersionCode = versionCode
        }
    }
}
