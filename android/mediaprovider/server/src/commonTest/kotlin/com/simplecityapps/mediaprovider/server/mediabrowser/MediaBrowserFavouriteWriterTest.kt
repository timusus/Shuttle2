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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class MediaBrowserFavouriteWriterTest {
    private class Rig(val mediaBrowserServer: MediaBrowserServer) {
        val server = FixtureServer { "" }
        val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
        val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), mediaBrowserServer.name.lowercase()).apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
        }
        val client = createHttpClient(server.engine)
        val writer = MediaBrowserFavouriteWriter(
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
        val path = "${mediaBrowserServer.apiPrefix}/Users/user456/FavoriteItems/item789"
        val song = Song(
            id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
            duration = 180_000, date = null, genres = emptyList(), path = "${mediaBrowserServer.songPathPrefix}item789", size = 0, mimeType = "audio/flac",
            lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
            externalId = "item789", mediaProvider = mediaBrowserServer.type, lyrics = null, grouping = null, bitRate = null,
            bitDepth = null, sampleRate = null, channelCount = null
        )
    }

    private fun eachServer(block: Rig.() -> Unit) = MediaBrowserServer.entries.forEach { Rig(it).block() }

    @Test
    fun `handles only its own server's songs`() = eachServer {
        writer.handles(song) shouldBe true
        writer.handles(song.copy(mediaProvider = MediaProviderType.Plex)) shouldBe false
    }

    @Test
    fun `favouriting POSTs to the user's FavoriteItems path`() = eachServer {
        server.respond(path, code = 200, method = "POST")

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe true

        val request = server.requestsTo(path).single()
        request.method shouldBe HttpMethod.Post
        when (mediaBrowserServer) {
            MediaBrowserServer.Jellyfin -> request.headers[HttpHeaders.Authorization]!!.contains("Token=\"token123\"") shouldBe true

            MediaBrowserServer.Emby -> {
                request.headers["X-Emby-Token"] shouldBe "token123"
                request.headers["X-Emby-Authorization"]!!.contains("DeviceId=\"device-1\"") shouldBe true
            }
        }
    }

    @Test
    fun `unfavouriting DELETEs the same path`() = eachServer {
        server.respond(path, code = 200, method = "DELETE")

        runBlocking { writer.setFavourite(song, favourite = false) } shouldBe true

        server.requestsTo(path).single().method shouldBe HttpMethod.Delete
    }

    @Test
    fun `a rejected call reports failure`() = eachServer {
        server.respond(path, code = 500, method = "POST")

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe false
    }

    @Test
    fun `nothing is sent when signed out or the song has no item id`() = eachServer {
        runBlocking { writer.setFavourite(song.copy(externalId = null), favourite = true) } shouldBe false
        credentialStore.authenticatedCredentials = null

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe false
        server.requests shouldBe emptyList()
    }
}
