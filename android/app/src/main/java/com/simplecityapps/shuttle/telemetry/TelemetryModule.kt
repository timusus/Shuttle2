package com.simplecityapps.shuttle.telemetry

import com.simplecityapps.shuttle.analytics.Analytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
@BindingContainer
abstract class TelemetryModule {
    @Binds
    abstract fun bindCrashReportingSdk(impl: SentryCrashReporting): CrashReportingSdk

    @Binds
    abstract fun bindAnalyticsSdk(impl: PostHogAnalytics): AnalyticsSdk

    @Binds
    abstract fun bindAnalytics(impl: PostHogAnalytics): Analytics
}
