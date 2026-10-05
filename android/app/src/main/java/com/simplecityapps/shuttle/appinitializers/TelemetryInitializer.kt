package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.telemetry.AnalyticsStartup
import com.simplecityapps.shuttle.telemetry.SentryBreadcrumbTree
import com.simplecityapps.shuttle.telemetry.TelemetryConsentGate
import dev.zacsweers.metro.Inject
import timber.log.Timber

/**
 * Applies the stored crash reporting choice synchronously, first of all the initializers, and schedules the analytics
 * choice for after the first frame ([AnalyticsStartup]).
 */
class TelemetryInitializer
@Inject
constructor(
    private val consentGate: TelemetryConsentGate,
    private val analyticsStartup: AnalyticsStartup
) : AppInitializer {
    override fun init(application: Application) {
        consentGate.startCrashReporting()
        analyticsStartup.schedule(application)

        if (!BuildConfig.DEBUG) {
            Timber.plant(SentryBreadcrumbTree())
        }
    }

    override fun priority(): Int = 3
}
