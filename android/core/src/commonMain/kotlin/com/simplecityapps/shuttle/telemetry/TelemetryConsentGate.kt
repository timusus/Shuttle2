package com.simplecityapps.shuttle.telemetry

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.settings.PrivacySettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Crash reporting (Sentry on Android and iOS), switched on and off by [TelemetryConsentGate]. */
interface CrashReportingSdk {
    /** Starts reporting, setting the SDK up the first time, or stops it. Idempotent; a no-op without a DSN. */
    fun setEnabled(enabled: Boolean)
}

/** Product analytics (PostHog on Android and iOS), switched on and off by [TelemetryConsentGate]. */
interface AnalyticsSdk {
    /** Starts collecting, setting the SDK up the first time, or stops it. Idempotent; a no-op without an API key. */
    fun setEnabled(enabled: Boolean)
}

/**
 * Each SDK runs only while its choice allows it. Both default to on for a user who never chose (#379, #481), and Home
 * says so once (`HomeEvent.AnalyticsNowOn`); turning either off in Settings > Privacy stops its SDK at once. [start]
 * applies the stored choices before anything can send an event or a crash, then follows every change to them.
 *
 * Crash reporting and analytics are separate choices ([PrivacySettings.crashReporting], [PrivacySettings.analytics]),
 * so each gates its own SDK. Shared by Android (`TelemetryInitializer`) and iOS (`IosTelemetryStartup`).
 */
@SingleIn(AppScope::class)
class TelemetryConsentGate @Inject constructor(
    private val privacySettings: PrivacySettings,
    private val crashReporting: CrashReportingSdk,
    private val analytics: AnalyticsSdk,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    fun start() {
        // Synchronously, so the stored choice is in force before the first event or crash
        crashReporting.setEnabled(privacySettings.crashReporting.value)
        analytics.setEnabled(privacySettings.analytics.value)

        // Each flow starts with the value just applied, which the SDKs ignore, then follows every toggle
        scope.launch { privacySettings.crashReporting.flow.collect(crashReporting::setEnabled) }
        scope.launch { privacySettings.analytics.flow.collect(analytics::setEnabled) }
    }
}
