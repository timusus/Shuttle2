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
 * Each SDK runs only while its choice allows it. Both default to on for a user who never chose (#379, #481): Android's
 * Home says so once to an upgrader (`HomeEvent.AnalyticsNowOn`), iOS's first-run welcome to everyone. Turning either
 * off in Settings > Privacy stops its SDK at once. [startCrashReporting] and [startAnalytics] apply the stored choice
 * before anything can send a crash or an event, then follow every change to it.
 *
 * Crash reporting and analytics are separate choices ([PrivacySettings.crashReporting], [PrivacySettings.analytics]),
 * so each gates its own SDK. Shared by Android (`TelemetryInitializer`, `AnalyticsStartup`) and iOS (`IosTelemetryStartup`),
 * which both start crash reporting at launch and analytics only once the first frame is up.
 */
@SingleIn(AppScope::class)
class TelemetryConsentGate @Inject constructor(
    private val privacySettings: PrivacySettings,
    private val crashReporting: CrashReportingSdk,
    private val analytics: AnalyticsSdk,
    @AppCoroutineScope private val scope: CoroutineScope,
) {
    fun startCrashReporting() {
        // Synchronously, so the stored choice is in force before the first crash; the flow starts with the value just
        // applied, which the SDK ignores, then follows every toggle
        crashReporting.setEnabled(privacySettings.crashReporting.value)
        scope.launch { privacySettings.crashReporting.flow.collect(crashReporting::setEnabled) }
    }

    fun startAnalytics() {
        // As crash reporting: the stored choice is in force before the first event
        analytics.setEnabled(privacySettings.analytics.value)
        scope.launch { privacySettings.analytics.flow.collect(analytics::setEnabled) }
    }
}
