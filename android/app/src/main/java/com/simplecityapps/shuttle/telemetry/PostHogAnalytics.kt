package com.simplecityapps.shuttle.telemetry

import android.app.Application
import com.posthog.PersonProfiles
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.analytics.Analytics
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * [Analytics] through PostHog. Set up the first time the user opts in ([TelemetryConsentGate]), then opted in and out
 * as they change their mind; [capture] drops every event while they're opted out. Without an API key (local builds,
 * tests) it never sets up at all.
 */
@SingleIn(AppScope::class)
class PostHogAnalytics @Inject constructor(
    private val application: Application
) : Analytics,
    AnalyticsSdk {
    @Volatile
    private var enabled = false

    private var setUp = false

    /** Super properties registered so far; handed to PostHog once it's set up, since opt-in can come later. */
    private val superProperties = mutableMapOf<String, Any>()

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

    override val isCapturing: Boolean get() = enabled && setUp

    override fun capture(
        event: String,
        properties: Map<String, Any>
    ) {
        if (isCapturing) {
            PostHog.capture(event, properties = properties)
        }
    }

    @Synchronized
    override fun register(
        name: String,
        value: Any
    ) {
        superProperties[name] = value
        if (setUp) PostHog.register(name, value)
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
            // No identify() call is ever made, so this never actually creates a person profile (#481): PostHog
            // stays anonymous, tracked only by its own generated device ID
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
        }
        PostHogAndroid.setup(application, config)
        PostHog.register("app_version", BuildConfig.VERSION_NAME)
        PostHog.register("app_version_code", BuildConfig.VERSION_CODE)
        PostHog.register("build_type", if (BuildConfig.DEBUG) "debug" else "release")
        superProperties.forEach { (name, value) -> PostHog.register(name, value) }
        setUp = true
    }
}
