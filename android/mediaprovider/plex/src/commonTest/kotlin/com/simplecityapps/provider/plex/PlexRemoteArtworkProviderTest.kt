package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.MediaContainer
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PlexRemoteArtworkProviderTest {
    // Each fixture is the response body itself
    private val server = FixtureServer { body -> body }

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

    private val provider = PlexRemoteArtworkProvider(authenticationManager, ItemsService(client))

    private val song = song(path = "plex:///library/metadata/107898")

    @Test
    fun `album artwork url is the item's parent thumb - without the plex token`() = runTest {
        respond(metadata(parentThumb = "/library/metadata/107898/thumb/1700000000"))

        provider.getAlbumArtworkUrl(song) shouldBe "${server.address}/library/metadata/107898/thumb/1700000000"

        server.requestsTo(ITEM).single().headers["X-Plex-Token"] shouldBe "token123"
    }

    @Test
    fun `requests to the signed-in server carry its token as a header`() {
        provider.requestHeaders("${server.address}/library/metadata/107898/thumb/1") shouldBe mapOf("X-Plex-Token" to "token123")
    }

    @Test
    fun `requests to any other host carry no token`() {
        provider.requestHeaders("https://api.shuttlemusicplayer.app/v1/artwork") shouldBe emptyMap()
    }

    @Test
    fun `signed out - requests carry no token`() {
        credentialStore.authenticatedCredentials = null

        provider.requestHeaders("${server.address}/library/metadata/1/thumb/1") shouldBe emptyMap()
    }

    @Test
    fun `album artwork falls back to the item's own thumb when it has no parent thumb`() = runTest {
        respond(metadata(parentThumb = null, thumb = "/library/metadata/107898/thumb/1700000000"))

        provider.getAlbumArtworkUrl(song) shouldBe
            "${server.address}/library/metadata/107898/thumb/1700000000"
    }

    @Test
    fun `artist artwork url is the item's grandparent thumb`() = runTest {
        respond(metadata(grandparentThumb = "/library/metadata/1/thumb/1700000000"))

        provider.getArtistArtworkUrl(song) shouldBe
            "${server.address}/library/metadata/1/thumb/1700000000"
    }

    @Test
    fun `no artwork url when the item carries no thumb`() = runTest {
        respond(metadata(parentThumb = null, thumb = null))

        provider.getAlbumArtworkUrl(song) shouldBe null
    }

    @Test
    fun `no artwork url when the server request fails`() = runTest {
        server.respond(ITEM, code = 500)

        provider.getAlbumArtworkUrl(song) shouldBe null
        credentialStore.authenticatedCredentials.shouldNotBeNull()
    }

    @Test
    fun `a rejected item request signs out`() = runTest {
        server.respond(ITEM, code = 401)

        provider.getAlbumArtworkUrl(song) shouldBe null
        credentialStore.authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `no artwork url for a song whose path carries no metadata key`() = runTest {
        provider.getAlbumArtworkUrl(song(path = "plex://item/107898")) shouldBe null
    }

    @Test
    fun `no artwork url when not authenticated`() = runTest {
        credentialStore.authenticatedCredentials = null

        provider.getAlbumArtworkUrl(song) shouldBe null
        server.requests.shouldBeEmpty()
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    private fun respond(metadata: Metadata) = server.respond(ITEM, S2Json.encodeToString(QueryResult(MediaContainer(metadata = listOf(metadata)))))

    private fun metadata(
        thumb: String? = null,
        parentThumb: String? = null,
        grandparentThumb: String? = null
    ) = Metadata(
        key = "/library/metadata/107898",
        type = "track",
        guid = "plex://track/1",
        index = 1,
        parentIndex = 1,
        title = "Song",
        duration = 180_000,
        parentTitle = "Album",
        grandparentTitle = "Artist",
        year = 2024,
        media = emptyList(),
        thumb = thumb,
        parentThumb = parentThumb,
        grandparentThumb = grandparentThumb
    )

    private fun song(path: String) = Song(
        id = 0,
        name = "Song",
        albumArtist = "Artist",
        artists = listOf("Artist"),
        album = "Album",
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = path,
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = "/library/parts/42/file.mp3",
        mediaProvider = MediaProviderType.Plex,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private companion object {
        const val ITEM = "/library/metadata/107898"
    }
}
