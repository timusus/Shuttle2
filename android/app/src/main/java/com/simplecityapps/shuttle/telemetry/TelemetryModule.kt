package com.simplecityapps.shuttle.telemetry

import com.simplecityapps.shuttle.analytics.Analytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class TelemetryModule {
    @Binds
    abstract fun bindCrashReportingSdk(impl: SentryCrashReporting): CrashReportingSdk

    @Binds
    abstract fun bindAnalyticsSdk(impl: PostHogAnalytics): AnalyticsSdk

    @Binds
    abstract fun bindAnalytics(impl: PostHogAnalytics): Analytics
}
