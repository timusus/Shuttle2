package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.PlaylistService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PlexPlaylistWriterTest {
    private val server = FixtureServer("plex")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = server.address
        // A Plex sign-in keeps the server's machine identifier as the user id
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "machine-1")
    }

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")
    )

    private val writer = PlexPlaylistWriter(authenticationManager, PlaylistService(client))

    private val items = "/playlists/77/items"

    @Test
    fun `reads every entry of the playlist with its playlistItemID and song path`() = runTest {
        server.respond(items, "playlist_entries.json")

        writer.entries("77") shouldBe PlaylistWriteResult.Success(
            listOf(
                ServerPlaylistEntry("5001", "plex:///library/metadata/101"),
                ServerPlaylistEntry("5002", "plex:///library/metadata/102")
            )
        )
        server.requestsTo(items).single().headers["X-Plex-Token"] shouldBe "token123"
    }

    @Test
    fun `adds tracks with one PUT naming them by the server's library uri`() = runTest {
        server.respond(items, code = 200, method = "PUT")

        writer.add("77", listOf("plex:///library/metadata/101", "plex:///library/metadata/102")) shouldBe PlaylistWriteResult.Success(Unit)

        val request = server.requestsTo(items).single()
        request.method shouldBe HttpMethod.Put
        request.url.parameters["uri"] shouldBe "server://machine-1/com.plexapp.plugins.library/library/metadata/101,102"
    }

    @Test
    fun `removes each entry with its own DELETE`() = runTest {
        server.respond("$items/5001", code = 200, method = "DELETE")
        server.respond("$items/5002", code = 200, method = "DELETE")

        writer.remove("77", listOf("5001", "5002")) shouldBe PlaylistWriteResult.Success(Unit)

        server.requests.map { request -> request.method to request.url.encodedPath } shouldBe listOf(
            HttpMethod.Delete to "$items/5001",
            HttpMethod.Delete to "$items/5002"
        )
    }

    @Test
    fun `moves an entry after another - or to the top without one`() = runTest {
        server.respond("$items/5002/move", code = 200, method = "PUT")

        writer.move("77", "5002", index = 1, after = "5001") shouldBe PlaylistWriteResult.Success(Unit)
        writer.move("77", "5002", index = 0, after = null) shouldBe PlaylistWriteResult.Success(Unit)

        server.requestsTo("$items/5002/move").map { request -> request.url.parameters["after"] } shouldBe listOf("5001", null)
    }

    @Test
    fun `a playlist the server doesn't have is gone`() = runTest {
        writer.remove("77", listOf("5001")) shouldBe PlaylistWriteResult.PlaylistGone
    }
}
