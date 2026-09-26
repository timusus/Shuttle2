package com.simplecityapps.shuttle

import android.content.SharedPreferences
import androidx.core.content.edit
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InstallDefaultsTest {
    private val prefs: SharedPreferences = RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }
    private val preferenceManager = GeneralPreferenceManager(prefs)
    private val privacySettings = PrivacySettings(SettingsStore(prefs))
    private val analyticsConsentSettings = AnalyticsConsentSettings(SettingsStore(prefs))
    private val installDefaults = InstallDefaults(preferenceManager, privacySettings, analyticsConsentSettings)

    @Test
    fun `a new install turns crash reporting and analytics on, has no changelog to show, and never needs the notice`() {
        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe true
        privacySettings.analytics.value shouldBe true
        preferenceManager.lastViewedChangelogVersion shouldBe "2026.09.25"
        preferenceManager.previousVersionCode shouldBe 26092501
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `an upgrade from a user who never chose turns crash reporting and analytics on and shows the changelog`() {
        preferenceManager.previousVersionCode = 26010101
        preferenceManager.lastViewedChangelogVersion = "2026.01.01"

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe true
        privacySettings.analytics.value shouldBe true
        preferenceManager.lastViewedChangelogVersion shouldBe "2026.01.01"
        preferenceManager.previousVersionCode shouldBe 26092501
    }

    @Test
    fun `an upgrade from a user who never chose does not mark the notice shown`() {
        preferenceManager.previousVersionCode = 26010101

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        analyticsConsentSettings.noticeShown.value shouldBe false
    }

    @Test
    fun `an upgrade from a user who answered the deleted consent card migrates analytics to an explicit off`() {
        preferenceManager.previousVersionCode = 26010101
        analyticsConsentSettings.asked.value = true

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.analytics.value shouldBe false
        privacySettings.analytics.isSet() shouldBe true
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `an upgrade keeps the choice a user made`() {
        preferenceManager.previousVersionCode = 26010101
        prefs.edit(commit = true) {
            putBoolean(PrivacySettings.CrashReporting.key, true)
            putBoolean(PrivacySettings.Analytics.key, true)
        }

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe true
        privacySettings.analytics.value shouldBe true
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `an upgrade keeps an explicit opt-out even from a user who answered the deleted consent card`() {
        preferenceManager.previousVersionCode = 26010101
        analyticsConsentSettings.asked.value = true
        privacySettings.analytics.value = true

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.analytics.value shouldBe true
    }

    @Test
    fun `a relaunch of a new install keeps a later opt-out`() {
        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")
        privacySettings.crashReporting.value = false
        privacySettings.analytics.value = false

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe false
        privacySettings.analytics.value shouldBe false
    }
}
