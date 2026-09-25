package com.simplecityapps.shuttle

import android.content.SharedPreferences
import androidx.core.content.edit
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
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
    private val installDefaults = InstallDefaults(preferenceManager, privacySettings)

    @Test
    fun `a new install turns crash reporting on and has no changelog to show`() {
        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe true
        preferenceManager.lastViewedChangelogVersion shouldBe "2026.09.25"
        preferenceManager.previousVersionCode shouldBe 26092501
    }

    @Test
    fun `an upgrade from a user who never chose keeps crash reporting off and shows the changelog`() {
        preferenceManager.previousVersionCode = 26010101
        preferenceManager.lastViewedChangelogVersion = "2026.01.01"

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe false
        preferenceManager.lastViewedChangelogVersion shouldBe "2026.01.01"
        preferenceManager.previousVersionCode shouldBe 26092501
    }

    @Test
    fun `an upgrade keeps the choice a user made`() {
        preferenceManager.previousVersionCode = 26010101
        prefs.edit(commit = true) { putBoolean(PrivacySettings.CrashReporting.key, true) }

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe true
    }

    @Test
    fun `a relaunch of a new install keeps a later opt-out`() {
        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")
        privacySettings.crashReporting.value = false

        installDefaults.onLaunch(versionCode = 26092501, versionName = "2026.09.25")

        privacySettings.crashReporting.value shouldBe false
    }
}
