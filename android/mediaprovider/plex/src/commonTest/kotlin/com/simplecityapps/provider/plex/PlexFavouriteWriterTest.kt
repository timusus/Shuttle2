package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.FavouriteService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PlexFavouriteWriterTest {
    private val server = FixtureServer("plex")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")
    )

    private val writer = PlexFavouriteWriter(authenticationManager, FavouriteService(client))

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 200_000, date = null, genres = emptyList(), path = "plex:///library/metadata/107898", size = 0,
        mimeType = "audio/mpeg", lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0,
        playbackPosition = 0, blacklisted = false, externalId = "/library/parts/42/file.mp3", mediaProvider = MediaProviderType.Plex,
        lyrics = null, grouping = null, bitRate = null, bitDepth = null, sampleRate = null, channelCount = null
    )

    private val rate = "/:/rate"
    private val metadata = "/library/metadata/107898"

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `handles only Plex songs`() {
        writer.handles(song) shouldBe true
        writer.handles(song.copy(mediaProvider = MediaProviderType.Jellyfin)) shouldBe false
    }

    @Test
    fun `favouriting rates the track 10 by its ratingKey - without reading it first`() = runTest {
        server.respond(rate, method = "PUT")

        writer.setFavourite(song, favourite = true) shouldBe true

        val request = server.requestsTo(rate).single()
        request.method shouldBe HttpMethod.Put
        request.headers["X-Plex-Token"] shouldBe "token123"
        request.url.parameters["key"] shouldBe "107898"
        request.url.parameters["identifier"] shouldBe "com.plexapp.plugins.library"
        request.url.parameters["rating"] shouldBe "10"
        server.requestsTo(metadata) shouldBe emptyList()
    }

    @Test
    fun `unfavouriting a track rated 10 reads its rating - then clears it`() = runTest {
        server.respond(metadata, fixture = "track_rated_10.json")
        server.respond(rate, method = "PUT")

        writer.setFavourite(song, favourite = false) shouldBe true

        server.requestsTo(metadata).single().headers["X-Plex-Token"] shouldBe "token123"
        server.requestsTo(rate).single().url.parameters["rating"] shouldBe "-1"
    }

    @Test
    fun `unfavouriting a track the user rated otherwise leaves its rating alone`() = runTest {
        server.respond(metadata, fixture = "track_rated_6.json")

        writer.setFavourite(song, favourite = false) shouldBe true

        server.requestsTo(rate) shouldBe emptyList()
    }

    @Test
    fun `unfavouriting an unrated track sends nothing`() = runTest {
        server.respond(metadata, fixture = "track_unrated.json")

        writer.setFavourite(song, favourite = false) shouldBe true

        server.requestsTo(rate) shouldBe emptyList()
    }

    @Test
    fun `a failed read of the rating fails the unfavourite so it is retried`() = runTest {
        server.respond(metadata, code = 500)

        writer.setFavourite(song, favourite = false) shouldBe false

        server.requestsTo(rate) shouldBe emptyList()
    }

    @Test
    fun `a rejected rate reports failure`() = runTest {
        server.respond(rate, code = 500, method = "PUT")

        writer.setFavourite(song, favourite = true) shouldBe false
    }

    @Test
    fun `nothing is sent when signed out or the path has no ratingKey`() = runTest {
        writer.setFavourite(song.copy(path = "plex://elsewhere"), favourite = true) shouldBe false
        credentialStore.authenticatedCredentials = null

        writer.setFavourite(song, favourite = true) shouldBe false
        server.requests shouldBe emptyList()
    }
}
