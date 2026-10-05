package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.ServerConnection
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest

class PlexConnectionsTest {
    private val remoteHttp = ServerConnection("http://198.51.100.4:32400")
    private val remoteHttps = ServerConnection("https://198-51-100-4.aaaa.plex.direct:32400")
    private val localHttp = ServerConnection("http://192.168.1.20:32400", local = true)
    private val localHttps = ServerConnection("https://192-168-1-20.aaaa.plex.direct:32400", local = true)
    private val relay = ServerConnection("https://10-0-0-1.aaaa.plex.direct:8443", relay = true)

    @Test
    fun `local comes before remote - the relay last - and https before http within each`() {
        orderConnections(listOf(relay, remoteHttp, localHttp, remoteHttps, localHttps)) shouldContainExactly
            listOf(localHttps, localHttp, remoteHttps, remoteHttp, relay)
    }

    @Test
    fun `the first connection in order that answers wins - even when a later one answers sooner`() = runTest {
        val reachable = firstReachable(listOf(relay, remoteHttps, localHttps)) { uri -> uri != localHttps.uri }

        reachable shouldBe remoteHttps.uri
    }

    @Test
    fun `a preferred connection that hangs doesn't hold up the answer once it's known - the rest are cancelled`() = runTest {
        val reachable = firstReachable(listOf(localHttps, remoteHttps)) { uri ->
            if (uri == localHttps.uri) true else awaitCancellation()
        }

        reachable shouldBe localHttps.uri
    }

    @Test
    fun `no answer from any connection is null`() = runTest {
        firstReachable(listOf(localHttps, relay)) { false }.shouldBeNull()
    }

    @Test
    fun `a connection matches its uri - or its plain address on its port - whatever the scheme`() {
        val connection = ServerConnection("https://192-168-1-20.aaaa.plex.direct:32400", local = true, address = "192.168.1.20", port = 32400)

        connection.matches("https://192-168-1-20.aaaa.plex.direct:32400") shouldBe true
        connection.matches("http://192.168.1.20:32400") shouldBe true
        connection.matches("http://192.168.1.20:32401") shouldBe false
        connection.matches("http://192.168.1.21:32400") shouldBe false
        connection.matches("not a url at all") shouldBe false
    }
}
