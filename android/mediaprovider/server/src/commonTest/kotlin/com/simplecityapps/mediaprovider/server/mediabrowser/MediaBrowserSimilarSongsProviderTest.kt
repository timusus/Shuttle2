package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class MediaBrowserSimilarSongsProviderTest {
    private class Rig(val mediaBrowserServer: MediaBrowserServer) {
        val server = FixtureServer { """{"Items":[{"Id":"a","Name":"A"},{"Id":"b"},{"Id":"a"},{"Id":"c"}],"TotalRecordCount":5}""" }
        val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
        val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), mediaBrowserServer.name.lowercase()).apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
        }
        val client = createHttpClient(server.engine)
        val provider = MediaBrowserSimilarSongsProvider(
            MediaBrowserAuthenticationManager(
                mediaBrowserServer,
                UserService(client, mediaBrowserServer, clientIdentity),
                credentialStore,
                clientIdentity,
                StreamProfile.Android
            ),
            client,
            clientIdentity
        )
        val path = "${mediaBrowserServer.apiPrefix}/Items/seed1/InstantMix"
        val seed = Song(
            id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
            duration = 180_000, date = null, genres = emptyList(), path = "${mediaBrowserServer.songPathPrefix}seed1", size = 0, mimeType = "audio/flac",
            lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
            externalId = "seed1", mediaProvider = mediaBrowserServer.type, lyrics = null, grouping = null, bitRate = null,
            bitDepth = null, sampleRate = null, channelCount = null
        )
    }

    private fun eachServer(block: suspend Rig.() -> Unit) = runTest { MediaBrowserServer.entries.forEach { Rig(it).block() } }

    @Test
    fun `handles only its own server's songs`() = eachServer {
        provider.handles(seed) shouldBe true
        provider.handles(seed.copy(mediaProvider = MediaProviderType.Plex)) shouldBe false
    }

    @Test
    fun `asks for the seed's instant mix for the user with a limit`() = eachServer {
        server.respond(path, "mix")

        provider.similarSongIds(seed, limit = 51)

        val request = server.requestsTo(path).single()
        request.url.parameters[mediaBrowserServer.itemsParameter("UserId")] shouldBe "user456"
        request.url.parameters[mediaBrowserServer.itemsParameter("Limit")] shouldBe "51"
    }

    @Test
    fun `returns the mix's item ids in order each once`() = eachServer {
        server.respond(path, "mix")

        provider.similarSongIds(seed, limit = 50) shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `a failing server has no similar songs`() = eachServer {
        server.respond(path, code = 500)

        provider.similarSongIds(seed, limit = 50) shouldBe emptyList()
    }

    @Test
    fun `nothing is sent when signed out or the song has no item id`() = eachServer {
        provider.similarSongIds(seed.copy(externalId = null), limit = 50) shouldBe emptyList()
        credentialStore.authenticatedCredentials = null

        provider.similarSongIds(seed, limit = 50) shouldBe emptyList()
        server.requests shouldBe emptyList()
    }
}
