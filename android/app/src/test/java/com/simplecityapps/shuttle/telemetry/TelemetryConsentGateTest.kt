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
    fun `nothing is collected before the user opts in`() = runTest {
        startGate()
        runCurrent()

        crashReporting.calls shouldBe listOf(false, false)
        analytics.calls shouldBe listOf(false, false)
        crashReporting.enabled shouldBe false
        analytics.enabled shouldBe false
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
    fun `opting in to analytics starts it and opting out stops it`() = runTest {
        startGate()
        runCurrent()

        privacySettings.analytics.value = true
        runCurrent()
        analytics.enabled shouldBe true
        crashReporting.enabled shouldBe false

        privacySettings.analytics.value = false
        runCurrent()
        analytics.enabled shouldBe false
    }

    @Test
    fun `opting in to crash reporting starts it and opting out stops it`() = runTest {
        startGate()
        runCurrent()

        privacySettings.crashReporting.value = true
        runCurrent()
        crashReporting.enabled shouldBe true
        analytics.enabled shouldBe false

        privacySettings.crashReporting.value = false
        runCurrent()
        crashReporting.enabled shouldBe false
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
