package com.simplecityapps.playback

import com.simplecityapps.playback.di.PlaybackEngineModule
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

// Robolectric: the app's ForegroundHold starts the real service class through a Context.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ForegroundHoldTest {
    private val context = RuntimeEnvironment.getApplication()

    private val foregroundHold = PlaybackEngineModule().provideForegroundHold(context)

    @Test
    fun `acquiring the hold starts the playback service in the foreground`() {
        foregroundHold.acquire()

        val started = shadowOf(context).nextStartedService
        started.component?.className shouldBe PlaybackService::class.java.name
        started.action shouldBe PlaybackService.ACTION_START
        foregroundHold.isHeld shouldBe true
    }

    @Test
    fun `releasing the hold starts nothing`() {
        foregroundHold.release()

        shadowOf(context).nextStartedService.shouldBeNull()
        foregroundHold.isHeld shouldBe false
    }

    @Test
    fun `awaitRelease returns once the hold is released`() = runTest(UnconfinedTestDispatcher()) {
        var released = false
        foregroundHold.acquire()
        launch(start = CoroutineStart.UNDISPATCHED) {
            foregroundHold.awaitRelease()
            released = true
        }
        released shouldBe false

        foregroundHold.release()
        released shouldBe true
    }

    @Test
    fun `awaitRelease returns at once with no hold`() = runTest {
        foregroundHold.awaitRelease()
    }
}
