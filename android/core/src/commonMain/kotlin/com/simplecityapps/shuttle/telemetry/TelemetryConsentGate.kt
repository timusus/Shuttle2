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

/** Each SDK runs only while its own choice allows it, applied before anything can send, so a stored off takes effect at once. */
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
