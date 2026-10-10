package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaylistWriteResult
import com.simplecityapps.mediaprovider.ServerPlaylistEntry
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.bodyText
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserPlaylistWriter
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class JellyfinPlaylistWriterTest {
    private val server = FixtureServer("jellyfin")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")

    private val authenticationManager = JellyfinAuthenticationManager(
        httpClient = client,
        credentialStore = credentialStore,
        clientIdentity = clientIdentity,
        streamProfile = StreamProfile.Android
    )

    private val writer = MediaBrowserPlaylistWriter(authenticationManager, client, clientIdentity)

    private val items = "/Playlists/pl1/Items"

    @Test
    fun `reads every entry of the playlist with its entry id and song path`() = runTest {
        server.respond(items, "playlist_entries.json")

        writer.entries("pl1") shouldBe PlaylistWriteResult.Success(
            listOf(
                ServerPlaylistEntry("entry-1", "jellyfin://item/song-1"),
                ServerPlaylistEntry("entry-2", "jellyfin://item/video-1"),
                ServerPlaylistEntry("entry-3", "jellyfin://item/song-3")
            )
        )
        val request = server.requestsTo(items).single()
        request.url.parameters["userId"] shouldBe "user456"
        // Every entry, whatever its type, so an index is the entry's place in the playlist
        request.url.parameters["includeItemTypes"] shouldBe null
        request.headers[HttpHeaders.Authorization]!!.contains("Token=\"token123\"") shouldBe true
    }

    @Test
    fun `adds songs by item id with a POST`() = runTest {
        server.respond(items, code = 204, method = "POST")

        writer.add("pl1", listOf("jellyfin://item/song-1", "jellyfin://item/song-2")) shouldBe PlaylistWriteResult.Success(Unit)

        val request = server.requestsTo(items).single()
        request.method shouldBe HttpMethod.Post
        request.url.parameters["ids"] shouldBe "song-1,song-2"
        request.url.parameters["userId"] shouldBe "user456"
    }

    @Test
    fun `removes entries by entry id with a DELETE`() = runTest {
        server.respond(items, code = 204, method = "DELETE")

        writer.remove("pl1", listOf("entry-1", "entry-3")) shouldBe PlaylistWriteResult.Success(Unit)

        val request = server.requestsTo(items).single()
        request.method shouldBe HttpMethod.Delete
        request.url.parameters["entryIds"] shouldBe "entry-1,entry-3"
    }

    @Test
    fun `moves an entry to its new index`() = runTest {
        server.respond("$items/entry-3/Move/0", code = 204, method = "POST")

        writer.move("pl1", "entry-3", index = 0, after = null) shouldBe PlaylistWriteResult.Success(Unit)

        server.requestsTo("$items/entry-3/Move/0").single().method shouldBe HttpMethod.Post
    }

    @Test
    fun `renames the playlist with just its new name`() = runTest {
        server.respond("/Playlists/pl1", code = 204, method = "POST")

        writer.rename("pl1", "Road trip") shouldBe PlaylistWriteResult.Success(Unit)

        val request = server.requestsTo("/Playlists/pl1").single()
        request.method shouldBe HttpMethod.Post
        request.bodyText shouldBe "{\"Name\":\"Road trip\"}"
    }

    @Test
    fun `deletes the playlist's item`() = runTest {
        server.respond("/Items/pl1", code = 204, method = "DELETE")

        writer.delete("pl1") shouldBe PlaylistWriteResult.Success(Unit)

        server.requestsTo("/Items/pl1").single().method shouldBe HttpMethod.Delete
    }

    @Test
    fun `a playlist the user may not delete is refused`() = runTest {
        server.respond("/Items/pl1", code = 403, method = "DELETE")

        writer.delete("pl1") shouldBe PlaylistWriteResult.Refused
    }

    @Test
    fun `a playlist the server doesn't have is gone`() = runTest {
        // FixtureServer answers anything it wasn't told about with a 404
        writer.add("pl1", listOf("jellyfin://item/song-1")) shouldBe PlaylistWriteResult.PlaylistGone
    }

    @Test
    fun `an edit the user may not make is refused`() = runTest {
        server.respond(items, code = 403, method = "POST")

        writer.add("pl1", listOf("jellyfin://item/song-1")) shouldBe PlaylistWriteResult.Refused
    }

    @Test
    fun `signed out - nothing is sent and the edit waits`() = runTest {
        credentialStore.authenticatedCredentials = null

        writer.add("pl1", listOf("jellyfin://item/song-1")) shouldBe PlaylistWriteResult.Failed
        server.requests shouldBe emptyList()
    }
}
