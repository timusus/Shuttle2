package com.simplecityapps.shuttle.telemetry

import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.settings.PrivacySettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Crash reporting (Sentry), switched on and off by [TelemetryConsentGate]. */
interface CrashReportingSdk {
    /** Starts reporting, setting the SDK up the first time, or stops it. Idempotent; a no-op without a DSN. */
    fun setEnabled(enabled: Boolean)
}

/** Product analytics (PostHog), switched on and off by [TelemetryConsentGate]. */
interface AnalyticsSdk {
    /** Starts collecting, setting the SDK up the first time, or stops it. Idempotent; a no-op without an API key. */
    fun setEnabled(enabled: Boolean)
}

/**
 * Nothing is collected until the user opts in. [start] applies the stored choices before anything can send an event or
 * a crash, then follows every change to them, from Settings > Privacy or the Home consent card, as it happens.
 *
 * Crash reporting and analytics are separate choices ([PrivacySettings.crashReporting], [PrivacySettings.analytics]),
 * so each gates its own SDK.
 */
@Singleton
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
