package com.simplecityapps.mediaprovider.server

import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class LanServerDiscoveryTest {
    private class FakeBroadcaster(
        var replies: List<String> = emptyList(),
        var failure: Exception? = null,
    ) : LanBroadcaster {
        val sent = mutableListOf<Pair<String, Int>>()

        override suspend fun broadcast(
            message: String,
            port: Int,
            listenMillis: Long,
        ): List<String> {
            sent += message to port
            failure?.let { throw it }
            return replies
        }
    }

    private val broadcaster = FakeBroadcaster()
    private val discovery = LanServerDiscovery(broadcaster)

    @Test
    fun `Jellyfin and Emby each ask with their own query on port 7359`() = runTest {
        discovery.discover(MediaProviderType.Jellyfin)
        discovery.discover(MediaProviderType.Emby)

        broadcaster.sent shouldBe listOf("who is JellyfinServer?" to 7359, "who is EmbyServer?" to 7359)
    }

    @Test
    fun `Plex and Subsonic don't broadcast`() = runTest {
        discovery.discover(MediaProviderType.Plex) shouldBe emptyList()
        discovery.discover(MediaProviderType.Subsonic) shouldBe emptyList()

        broadcaster.sent shouldBe emptyList()
    }

    @Test
    fun `each server's reply becomes a suggestion - once - and anything else is ignored`() = runTest {
        broadcaster.replies = listOf(
            """{"Address":"http://192.168.1.10:8096/","Id":"a1","Name":"Living Room","EndpointAddress":null}""",
            """{"Address":"http://192.168.1.10:8096","Id":"a1","Name":"Living Room"}""",
            """{"Address":"https://nas.local:8920","Id":"b2","Name":"NAS"}""",
            "who is JellyfinServer?",
            """{"Id":"c3","Name":"No address"}""",
            """{"Address":"ftp://192.168.1.11","Id":"d4","Name":"Not http"}""",
        )

        discovery.discover(MediaProviderType.Jellyfin) shouldBe listOf(
            DiscoveredServer("Living Room", "http://192.168.1.10:8096"),
            DiscoveredServer("NAS", "https://nas.local:8920"),
        )
    }

    @Test
    fun `a reply with no name is shown by its address`() = runTest {
        broadcaster.replies = listOf("""{"Address":"http://192.168.1.10:8096","Id":"a1"}""")

        discovery.discover(MediaProviderType.Emby) shouldBe listOf(DiscoveredServer("http://192.168.1.10:8096", "http://192.168.1.10:8096"))
    }

    @Test
    fun `a broadcast that fails finds nothing`() = runTest {
        broadcaster.failure = IllegalStateException("Network is unreachable")

        discovery.discover(MediaProviderType.Jellyfin) shouldBe emptyList()
    }
}
