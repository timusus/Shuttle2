package com.simplecityapps.playback

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** When Cast is set up: once the app comes to the foreground, or once the playback service's session starts. */
@RunWith(RobolectricTestRunner::class)
class CastStarterTest {
    private val application: Application = RuntimeEnvironment.getApplication()

    private val localPlayer: ExoPlayer = TestExoPlayerBuilder(application).build()

    private var castPlayersBuilt = 0

    private val castStarter =
        CastStarter(
            AppPlayer(localPlayer) {
                castPlayersBuilt++
                null
            }
        )

    @After
    fun tearDown() {
        localPlayer.release()
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `a session started with no activity sets Cast up, once, on a main thread message of its own`() {
        castStarter.startInForeground(application)

        castStarter.startForSession()
        castPlayersBuilt shouldBe 0

        idleMainLooper()
        castPlayersBuilt shouldBe 1

        // The app coming to the foreground later, or the session starting again, doesn't set it up again.
        Robolectric.buildActivity(Activity::class.java).setup()
        castStarter.startForSession()
        idleMainLooper()
        castPlayersBuilt shouldBe 1
    }

    @Test
    fun `the app sets Cast up only once an activity starts`() {
        castStarter.startInForeground(application)
        idleMainLooper()
        castPlayersBuilt shouldBe 0

        Robolectric.buildActivity(Activity::class.java).setup()
        idleMainLooper()
        castPlayersBuilt shouldBe 1

        // A session started after that doesn't set it up again.
        castStarter.startForSession()
        idleMainLooper()
        castPlayersBuilt shouldBe 1
    }
}
