package com.simplecityapps.shuttle.telemetry

import android.app.Application
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.analytics.Analytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Analytics] through PostHog. Set up the first time the user opts in ([TelemetryConsentGate]), then opted in and out
 * as they change their mind; [capture] drops every event while they're opted out. Without an API key (local builds,
 * tests) it never sets up at all.
 */
@Singleton
class PostHogAnalytics @Inject constructor(
    private val application: Application
) : Analytics,
    AnalyticsSdk {
    @Volatile
    private var enabled = false

    private var setUp = false

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (BuildConfig.POSTHOG_API_KEY.isBlank()) return
        if (enabled) {
            if (!setUp) setUp()
            PostHog.optIn()
        } else if (setUp) {
            PostHog.optOut()
        }
    }

    override fun capture(
        event: String,
        properties: Map<String, Any>
    ) {
        if (enabled && setUp) {
            PostHog.capture(event, properties = properties)
        }
    }

    private fun setUp() {
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_API_KEY,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            captureScreenViews = false
            captureDeepLinks = false
            captureApplicationLifecycleEvents = true
            sessionReplay = false
            debug = BuildConfig.DEBUG
            // Keeps the anonymous person profile, so retention queries see the install cohort
            personProfiles = PersonProfiles.ALWAYS
        }
        PostHogAndroid.setup(application, config)
        PostHog.register("app_version", BuildConfig.VERSION_NAME)
        PostHog.register("app_version_code", BuildConfig.VERSION_CODE)
        PostHog.register("build_type", if (BuildConfig.DEBUG) "debug" else "release")
        setUp = true
    }
}
