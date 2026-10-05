package com.simplecityapps.shuttle.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.di.IoDispatcher
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Sets PostHog up once, after the first frame, off the main thread (#914), as iOS does. Sentry is started
 * synchronously at launch ([TelemetryConsentGate.startCrashReporting]); only analytics waits.
 *
 * Consent is unchanged: [TelemetryConsentGate.startAnalytics] applies the stored choice before PostHog is set up, and
 * [PostHogAnalytics] never sets up or sends without it. Events captured before this runs are dropped deliberately
 * ([Analytics.isCapturing] is false until setup), not queued: nothing sends one in the first frames.
 *
 * Setup runs when the first activity has drawn its first frame; a process that never shows one (service, Android Auto)
 * sets up after [FALLBACK_DELAY_MS] instead. PostHog sends "Application Opened" from an activity-started callback it
 * registers at setup, which has already been missed once an activity is started, so [start] sends it itself then.
 */
@SingleIn(AppScope::class)
class AnalyticsStartup @Inject constructor(
    private val consentGate: TelemetryConsentGate,
    private val analytics: Analytics,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val started = AtomicBoolean(false)

    @Volatile
    internal var activityStarted = false

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Schedules [start] for after the first frame, or the first idle fallback. Call once, from the main thread. */
    fun schedule(application: Application) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ start() }, FALLBACK_DELAY_MS)
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                activityStarted = true
            }

            override fun onActivityResumed(activity: Activity) {
                application.unregisterActivityLifecycleCallbacks(this)
                // A callback in the frame's own pass, then a message behind it: runs once that frame is drawn
                Choreographer.getInstance().postFrameCallback { handler.post { start() } }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** Applies the stored analytics choice, setting PostHog up if it allows it. Runs once; later calls do nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val missedOpen = activityStarted
        scope.launch {
            consentGate.startAnalytics()
            if (missedOpen && analytics.isCapturing) {
                analytics.capture(
                    "Application Opened",
                    mapOf(
                        "from_background" to false,
                        "version" to BuildConfig.VERSION_NAME,
                        "build" to BuildConfig.VERSION_CODE.toString()
                    )
                )
            }
        }
    }

    companion object {
        const val FALLBACK_DELAY_MS = 5_000L
    }
}
