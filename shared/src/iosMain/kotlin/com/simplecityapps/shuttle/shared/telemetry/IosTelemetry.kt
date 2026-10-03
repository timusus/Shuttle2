package com.simplecityapps.shuttle.shared.telemetry

import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.telemetry.AnalyticsSdk
import com.simplecityapps.shuttle.telemetry.CrashReportingSdk
import com.simplecityapps.shuttle.telemetry.TelemetryScrubber
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Sentry, set up in Swift (`SentryCrashReporter`). Swift calls Kotlin on main; these may be called from any thread. */
interface IosCrashReporter {
    /** Starts Sentry, the first time, or closes it. Idempotent; a no-op without a DSN. */
    fun setEnabled(enabled: Boolean)

    /** A warning or error shared code logged, already [scrubbed][TelemetryScrubber]; dropped while Sentry is off. */
    fun addBreadcrumb(
        category: String,
        message: String,
        isError: Boolean
    )
}

/** PostHog, set up in Swift (`PostHogProductAnalytics`). */
interface IosProductAnalytics {
    /** Opts in, setting PostHog up the first time, or opts out. Idempotent; a no-op without an API key. */
    fun setEnabled(enabled: Boolean)

    /** One event; dropped while opted out. */
    fun capture(
        event: String,
        properties: Map<String, Any>
    )

    /** Super properties sent with every later event, replacing earlier values by name; kept until PostHog is set up. */
    fun register(properties: Map<String, Any>)
}

/**
 * What only Swift can make for telemetry, handed to [com.simplecityapps.shuttle.shared.createIosAppGraph]: the two
 * SDKs, which the shared `TelemetryConsentGate` starts and stops ([IosTelemetryStartup]). [None] sends nothing, for
 * tests and previews.
 */
class IosTelemetry(
    val crashReporter: IosCrashReporter,
    val analytics: IosProductAnalytics
) {
    companion object {
        val None = IosTelemetry(
            crashReporter = object : IosCrashReporter {
                override fun setEnabled(enabled: Boolean) = Unit

                override fun addBreadcrumb(
                    category: String,
                    message: String,
                    isError: Boolean
                ) = Unit
            },
            analytics = object : IosProductAnalytics {
                override fun setEnabled(enabled: Boolean) = Unit

                override fun capture(
                    event: String,
                    properties: Map<String, Any>
                ) = Unit

                override fun register(properties: Map<String, Any>) = Unit
            },
        )
    }
}

/** The shared seams over Swift's SDKs: the consent gate's two switches, and the events shared code captures. */
@ContributesTo(AppScope::class)
@BindingContainer
object IosTelemetryModule {
    @Provides
    fun provideCrashReportingSdk(telemetry: IosTelemetry): CrashReportingSdk = object : CrashReportingSdk {
        override fun setEnabled(enabled: Boolean) = telemetry.crashReporter.setEnabled(enabled)
    }

    @Provides
    fun provideAnalyticsSdk(telemetry: IosTelemetry): AnalyticsSdk = object : AnalyticsSdk {
        override fun setEnabled(enabled: Boolean) = telemetry.analytics.setEnabled(enabled)
    }

    @Provides
    fun provideAnalytics(telemetry: IosTelemetry): Analytics = object : Analytics {
        override fun capture(
            event: String,
            properties: Map<String, Any>
        ) = telemetry.analytics.capture(event, properties)
    }
}

/** [TelemetryScrubber] for Swift's Sentry hooks: `TelemetryKt.scrubForTelemetry(text:)`. */
fun scrubForTelemetry(text: String): String = TelemetryScrubber.scrub(text)
