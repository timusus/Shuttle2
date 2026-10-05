package com.simplecityapps.shuttle.telemetry

import android.app.Activity
import android.app.Application
import android.os.Handler
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsStartupTest {
    private val consentGate = mockk<TelemetryConsentGate>(relaxed = true)
    private val startup = AnalyticsStartup(consentGate, UnconfinedTestDispatcher()).apply {
        // Robolectric's Choreographer is not driven by the test: run the post-frame step directly
        afterNextFrame = { handler: Handler, block: () -> Unit -> handler.post(block) }
    }
    private val application = RuntimeEnvironment.getApplication() as Application

    private fun resumeActivity(): ActivityController<Activity> = Robolectric.buildActivity(Activity::class.java).setup()

    @Test
    fun `nothing is set up until the startup runs`() {
        startup.schedule(application)

        verify(exactly = 0) { consentGate.startAnalytics() }
    }

    @Test
    fun `the deferred setup runs once however often it is triggered`() {
        startup.start()
        startup.start()
        startup.start()

        verify(exactly = 1) { consentGate.startAnalytics() }
    }

    @Test
    fun `setup runs after the first activity resumes, then the fallback does not run it again`() {
        startup.schedule(application)

        resumeActivity()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        verify(exactly = 1) { consentGate.startAnalytics() }

        shadowOf(android.os.Looper.getMainLooper()).idleFor(AnalyticsStartup.FALLBACK_DELAY_MS * 2, TimeUnit.MILLISECONDS)
        verify(exactly = 1) { consentGate.startAnalytics() }
    }

    @Test
    fun `a second activity after the first does not run setup again`() {
        startup.schedule(application)

        resumeActivity()
        resumeActivity()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        verify(exactly = 1) { consentGate.startAnalytics() }
    }

    @Test
    fun `the fallback runs setup when no activity starts, and stops listening for one`() {
        startup.schedule(application)

        shadowOf(android.os.Looper.getMainLooper()).idleFor(AnalyticsStartup.FALLBACK_DELAY_MS - 1, TimeUnit.MILLISECONDS)
        verify(exactly = 0) { consentGate.startAnalytics() }

        shadowOf(android.os.Looper.getMainLooper()).idleFor(2, TimeUnit.MILLISECONDS)
        verify(exactly = 1) { consentGate.startAnalytics() }

        resumeActivity()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        verify(exactly = 1) { consentGate.startAnalytics() }
    }
}
