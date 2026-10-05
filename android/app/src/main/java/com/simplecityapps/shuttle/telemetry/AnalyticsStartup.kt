package com.simplecityapps.shuttle.telemetry

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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
 * (`Analytics.isCapturing` is false until setup), not queued. The one event that must not be lost, the once-per-install
 * `entitlement_resolved`, waits for `Analytics.capturing` instead of being offered once.
 *
 * Setup runs when the first activity has drawn its first frame; a process that never shows one (service, Android Auto)
 * sets up after [FALLBACK_DELAY_MS] instead. PostHog sends "Application Opened" itself: its process lifecycle observer
 * is added on the main thread at setup, and a late observer on an already started lifecycle is replayed `onStart`.
 */
@SingleIn(AppScope::class)
class AnalyticsStartup @Inject constructor(
    private val consentGate: TelemetryConsentGate,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    private val started = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Posts [block] to run once the frame being prepared has been drawn. Replaceable in tests. */
    internal var afterNextFrame: (Handler, () -> Unit) -> Unit = { handler, block ->
        // A callback in the frame's own pass, then a message behind it: runs once that frame is drawn
        Choreographer.getInstance().postFrameCallback { handler.post(block) }
    }

    /** Schedules [start] for after the first frame, or the first idle fallback. Call once, from the main thread. */
    fun schedule(application: Application) {
        val handler = Handler(Looper.getMainLooper())
        lateinit var callbacks: Application.ActivityLifecycleCallbacks
        val fallback = Runnable {
            application.unregisterActivityLifecycleCallbacks(callbacks)
            start()
        }
        callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                application.unregisterActivityLifecycleCallbacks(this)
                handler.removeCallbacks(fallback)
                afterNextFrame(handler) { start() }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        handler.postDelayed(fallback, FALLBACK_DELAY_MS)
    }

    /** Applies the stored analytics choice, setting PostHog up if it allows it. Runs once; later calls do nothing. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { consentGate.startAnalytics() }
    }

    companion object {
        const val FALLBACK_DELAY_MS = 5_000L
    }
}
