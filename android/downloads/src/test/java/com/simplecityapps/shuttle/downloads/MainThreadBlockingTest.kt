package com.simplecityapps.shuttle.downloads

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.LooperMode

@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
class MainThreadBlockingTest {
    @Test
    fun `runs the block directly when already on main`() {
        val ranOnMain = runOnMainThreadBlocking { Looper.myLooper() == Looper.getMainLooper() }

        ranOnMain shouldBe true
    }

    @Test
    fun `hops to main when called from a background thread`() {
        var ranOnMain = false

        val thread =
            Thread {
                runOnMainThreadBlocking { ranOnMain = Looper.myLooper() == Looper.getMainLooper() }
            }
        thread.start()
        // The post to main only runs once Robolectric's paused main looper is drained.
        while (thread.isAlive) {
            Robolectric.flushForegroundThreadScheduler()
        }
        thread.join()

        ranOnMain shouldBe true
    }
}
