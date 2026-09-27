package com.simplecityapps.playback.chromecast

import com.google.android.gms.cast.framework.CastSession
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The phone's server runs whichever way a session comes up: started, resumed, or (when a session is already up as Cast's
 * context loads) reported only as started. Keeping it up when [CastSessionManager.start] finds a session already up
 * needs Cast's context, so it's a device check (docs/testing/device-checks.md).
 */
// Robolectric: runs a real HTTP server against RuntimeEnvironment.getApplication().
@RunWith(RobolectricTestRunner::class)
class CastSessionManagerTest {
    private val streams = CastStreams(FakeMediaInfoProvider(), EmptyCoroutineContext)

    private val server = HttpServer(
        CastService(RuntimeEnvironment.getApplication(), FakeSongRepository(emptyList()), FakeArtworkImageLoader(ByteArray(0)), streams),
        streams,
        port = 0
    )

    private val manager = CastSessionManager(RuntimeEnvironment.getApplication(), server, streams)

    private val session = mockk<CastSession>()

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `a session reported only as started starts the server, keeping the key`() {
        val key = streams.key

        manager.onSessionStarted(session, "session")

        server.isAlive shouldBe true
        streams.key shouldBe key
    }

    @Test
    fun `a resumed session starts the server, keeping the key`() {
        val key = streams.key

        manager.onSessionResumed(session, false)

        server.isAlive shouldBe true
        streams.key shouldBe key
    }

    @Test
    fun `a session starting starts the server with a new key, and ending stops it`() {
        val key = streams.key

        manager.onSessionStarting(session)
        manager.onSessionStarted(session, "session")

        server.isAlive shouldBe true
        (streams.key == key) shouldBe false

        manager.onSessionEnded(session, 0)

        server.isAlive shouldBe false
    }
}
