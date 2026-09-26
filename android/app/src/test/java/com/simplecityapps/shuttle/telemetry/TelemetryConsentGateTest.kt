package com.simplecityapps.shuttle.telemetry

import android.content.SharedPreferences
import androidx.core.content.edit
import com.simplecityapps.shuttle.settings.PrivacySettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TelemetryConsentGateTest {
    private val prefs: SharedPreferences = RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }
    private val privacySettings = PrivacySettings(SettingsStore(prefs))
    private val crashReporting = FakeSdk()
    private val analytics = FakeSdk()

    private fun TestScope.startGate() {
        TelemetryConsentGate(privacySettings, FakeCrashReporting(crashReporting), FakeAnalytics(analytics), backgroundScope).start()
    }

    @Test
    fun `both default on for a user who never chose`() = runTest {
        startGate()
        runCurrent()

        crashReporting.calls shouldBe listOf(true, true)
        analytics.calls shouldBe listOf(true, true)
        crashReporting.enabled shouldBe true
        analytics.enabled shouldBe true
    }

    @Test
    fun `an explicit off stays off`() = runTest {
        prefs.edit(commit = true) {
            putBoolean(PrivacySettings.CrashReporting.key, false)
            putBoolean(PrivacySettings.Analytics.key, false)
        }

        startGate()

        crashReporting.calls shouldBe listOf(false)
        analytics.calls shouldBe listOf(false)
    }

    @Test
    fun `the stored choice is applied at startup, before any coroutine runs`() = runTest {
        prefs.edit(commit = true) {
            putBoolean(PrivacySettings.CrashReporting.key, true)
            putBoolean(PrivacySettings.Analytics.key, true)
        }

        startGate()

        crashReporting.calls shouldBe listOf(true)
        analytics.calls shouldBe listOf(true)
    }

    @Test
    fun `opting out of analytics stops it, independently of crash reporting`() = runTest {
        startGate()
        runCurrent()

        privacySettings.analytics.value = false
        runCurrent()
        analytics.enabled shouldBe false
        crashReporting.enabled shouldBe true

        privacySettings.analytics.value = true
        runCurrent()
        analytics.enabled shouldBe true
    }

    @Test
    fun `opting out of crash reporting stops it, independently of analytics`() = runTest {
        startGate()
        runCurrent()

        privacySettings.crashReporting.value = false
        runCurrent()
        crashReporting.enabled shouldBe false
        analytics.enabled shouldBe true

        privacySettings.crashReporting.value = true
        runCurrent()
        crashReporting.enabled shouldBe true
    }

    /** Records every [setEnabled] call the gate makes, standing in for Sentry or PostHog. */
    private class FakeSdk {
        val calls = mutableListOf<Boolean>()
        val enabled: Boolean? get() = calls.lastOrNull()

        fun setEnabled(enabled: Boolean) {
            calls += enabled
        }
    }

    private class FakeCrashReporting(private val sdk: FakeSdk) : CrashReportingSdk {
        override fun setEnabled(enabled: Boolean) = sdk.setEnabled(enabled)
    }

    private class FakeAnalytics(private val sdk: FakeSdk) : AnalyticsSdk {
        override fun setEnabled(enabled: Boolean) = sdk.setEnabled(enabled)
    }
}
