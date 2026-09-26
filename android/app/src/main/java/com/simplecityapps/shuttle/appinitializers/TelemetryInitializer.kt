package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.telemetry.SentryBreadcrumbTree
import com.simplecityapps.shuttle.telemetry.TelemetryConsentGate
import javax.inject.Inject
import timber.log.Timber

/** Applies the stored crash reporting and analytics choices, first of all the initializers. */
class TelemetryInitializer
@Inject
constructor(
    private val consentGate: TelemetryConsentGate
) : AppInitializer {
    override fun init(application: Application) {
        consentGate.start()

        if (!BuildConfig.DEBUG) {
            Timber.plant(SentryBreadcrumbTree())
        }
    }

    override fun priority(): Int = 3
}
