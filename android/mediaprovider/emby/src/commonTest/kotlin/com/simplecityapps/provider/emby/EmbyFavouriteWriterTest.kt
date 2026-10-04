package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.http.FavouriteService
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class EmbyFavouriteWriterTest {
    private val server = FixtureServer("emby")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val authenticationManager = EmbyAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
        streamProfile = StreamProfile.Android
    )

    private val writer = EmbyFavouriteWriter(authenticationManager, FavouriteService(client))

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 180_000, date = null, genres = emptyList(), path = "emby://item/item789", size = 0, mimeType = "audio/flac",
        lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
        externalId = "item789", mediaProvider = MediaProviderType.Emby, lyrics = null, grouping = null, bitRate = null,
        bitDepth = null, sampleRate = null, channelCount = null
    )

    @Test
    fun `handles only Emby songs`() {
        writer.handles(song) shouldBe true
        writer.handles(song.copy(mediaProvider = MediaProviderType.Plex)) shouldBe false
    }

    @Test
    fun `favouriting POSTs to the user's FavoriteItems path`() {
        server.respond("/emby/Users/user456/FavoriteItems/item789", code = 200, method = "POST")

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe true

        val request = server.requestsTo("/emby/Users/user456/FavoriteItems/item789").single()
        request.method shouldBe HttpMethod.Post
        request.headers["X-Emby-Token"] shouldBe "token123"
        request.headers["X-Emby-Authorization"]!!.contains("DeviceId=\"device-1\"") shouldBe true
    }

    @Test
    fun `unfavouriting DELETEs the same path`() {
        server.respond("/emby/Users/user456/FavoriteItems/item789", code = 200, method = "DELETE")

        runBlocking { writer.setFavourite(song, favourite = false) } shouldBe true

        server.requestsTo("/emby/Users/user456/FavoriteItems/item789").single().method shouldBe HttpMethod.Delete
    }

    @Test
    fun `a rejected call reports failure`() {
        server.respond("/emby/Users/user456/FavoriteItems/item789", code = 500, method = "POST")

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe false
    }

    @Test
    fun `nothing is sent when signed out or the song has no item id`() {
        runBlocking { writer.setFavourite(song.copy(externalId = null), favourite = true) } shouldBe false
        credentialStore.authenticatedCredentials = null

        runBlocking { writer.setFavourite(song, favourite = true) } shouldBe false
        server.requests shouldBe emptyList()
    }
}
