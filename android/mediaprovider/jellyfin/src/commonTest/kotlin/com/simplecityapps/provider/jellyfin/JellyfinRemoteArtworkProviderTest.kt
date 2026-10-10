package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.mediabrowser.ItemsService
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServer
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Artwork urls for Jellyfin songs, which have no image when the server doesn't know the song's album or artist. */
class JellyfinRemoteArtworkProviderTest {
    private val server = FixtureServer("jellyfin")

    private val client = createHttpClient(server.engine)

    private val credentialStore =
        ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin").apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
        }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")

    private val provider =
        JellyfinRemoteArtworkProvider(
            authenticationManager =
                JellyfinAuthenticationManager(
                    httpClient = client,
                    credentialStore = credentialStore,
                    clientIdentity = clientIdentity,
                    streamProfile = StreamProfile.Android
                ),
            itemsService = ItemsService(client, MediaBrowserServer.Jellyfin, clientIdentity)
        )

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `album artwork is the album's primary image`() = runTest {
        server.respond("/Users/user-1/Items/song-2", "item.json")

        provider.getAlbumArtworkUrl(song("jellyfin://item/song-2")) shouldBe "${server.address}/Items/album-1/Images/Primary?maxWidth=1000&maxHeight=1000"
        server.requests.single().headers["Authorization"]!! shouldContain "Token=\"token-1\""
    }

    @Test
    fun `artist artwork is the named artist's primary image - without asking for the song`() = runTest {
        provider.getArtistArtworkUrl(song("jellyfin://item/song-2"), serverArtistId = "artist-1") shouldBe "${server.address}/Items/artist-1/Images/Primary?maxWidth=1000&maxHeight=1000"
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `a song without an album has no album artwork`() = runTest {
        server.respond("/Users/user-1/Items/song-4", "loose_item.json")

        provider.getAlbumArtworkUrl(song("jellyfin://item/song-4")) shouldBe null
    }

    @Test
    fun `no artwork when the server can't find the song`() = runTest {
        provider.getAlbumArtworkUrl(song("jellyfin://item/song-2")) shouldBe null
    }

    @Test
    fun `no artwork for a path without an item id - without asking the server`() = runTest {
        provider.getAlbumArtworkUrl(song("jellyfin://item")) shouldBe null
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no artwork when signed out`() = runTest {
        credentialStore.authenticatedCredentials = null

        provider.getAlbumArtworkUrl(song("jellyfin://item/song-2")) shouldBe null
        provider.getArtistArtworkUrl(song("jellyfin://item/song-2"), serverArtistId = "artist-1") shouldBe null
        server.requests.shouldBeEmpty()
    }

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
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = path.substringAfterLast('/'),
        mediaProvider = MediaProviderType.Jellyfin,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
