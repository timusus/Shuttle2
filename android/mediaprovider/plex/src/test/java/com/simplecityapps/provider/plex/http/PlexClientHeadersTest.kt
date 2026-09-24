package com.simplecityapps.provider.plex.http

import com.simplecityapps.mediaprovider.ClientIdentity
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlexClientHeadersTest {
    @Test
    fun `plexClientHeaders emits the client identity on every field`() {
        val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

        plexClientHeaders(clientIdentity) shouldBe mapOf(
            "X-Plex-Client-Identifier" to "device-1",
            "X-Plex-Product" to "Shuttle2.0",
            "X-Plex-Version" to "2026.09.24",
            "X-Plex-Platform" to "Android",
            "X-Plex-Device-Name" to "Pixel"
        )
    }
}
