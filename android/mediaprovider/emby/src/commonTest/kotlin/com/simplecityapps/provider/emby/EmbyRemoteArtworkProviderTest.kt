package com.simplecityapps.provider.emby

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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Artwork urls for Emby songs, which have no image when the server doesn't know the song's album or artist (#525). */
class EmbyRemoteArtworkProviderTest {
    private val server = FixtureServer("emby")

    private val client = createHttpClient(server.engine)

    private val credentialStore =
        ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
        }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")

    private val provider =
        EmbyRemoteArtworkProvider(
            authenticationManager =
                EmbyAuthenticationManager(
                    httpClient = client,
                    credentialStore = credentialStore,
                    clientIdentity = clientIdentity,
                    streamProfile = StreamProfile.Android
                ),
            itemsService = ItemsService(client, MediaBrowserServer.Emby, clientIdentity)
        )

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `album artwork is the album's primary image`() = runTest {
        server.respond("/Users/user-1/Items/102", "item.json")

        provider.getAlbumArtworkUrl(song("emby://item/102")) shouldBe "${server.address}/Items/201/Images/Primary?maxWidth=1000&maxHeight=1000"
        server.requests.single().headers["X-Emby-Token"] shouldBe "token-1"
    }

    @Test
    fun `a song whose album has no image shows its own primary image`() = runTest {
        server.respond("/Users/user-1/Items/105", "item_own_image.json")
        server.respond("/Users/user-1/Items/202", "album_no_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/105")) shouldBe "${server.address}/Items/105/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song with its own image still shows the album's image when Emby sends no album image tag`() = runTest {
        server.respond("/Users/user-1/Items/105", "item_own_image.json")
        server.respond("/Users/user-1/Items/202", "album_with_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/105")) shouldBe "${server.address}/Items/202/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song borrowing a sibling's image shows the album's image when the album has one`() = runTest {
        server.respond("/Users/user-1/Items/202", "album_with_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/107").copy(serverAlbumId = "202", artworkItemId = "105")) shouldBe "${server.address}/Items/202/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song borrowing a sibling's image shows it when the album has none`() = runTest {
        server.respond("/Users/user-1/Items/202", "album_no_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/107").copy(serverAlbumId = "202", artworkItemId = "105")) shouldBe "${server.address}/Items/105/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song borrowing a sibling's image shows it when the album lookup fails`() = runTest {
        server.respond("/Users/user-1/Items/202", code = 500)

        provider.getAlbumArtworkUrl(song("emby://item/107").copy(serverAlbumId = "202", artworkItemId = "105")) shouldBe "${server.address}/Items/105/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a failed album lookup is asked again next time`() = runTest {
        val sibling = song("emby://item/107").copy(serverAlbumId = "202", artworkItemId = "105")
        server.respond("/Users/user-1/Items/202", code = 500)
        provider.getAlbumArtworkUrl(sibling)

        server.respond("/Users/user-1/Items/202", "album_with_image.json")

        provider.getAlbumArtworkUrl(sibling) shouldBe "${server.address}/Items/202/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song with its own image shows it when the album lookup fails`() = runTest {
        server.respond("/Users/user-1/Items/105", "item_own_image.json")
        server.respond("/Users/user-1/Items/202", code = 500)

        provider.getAlbumArtworkUrl(song("emby://item/105")) shouldBe "${server.address}/Items/105/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `songs of one album share a single album lookup`() = runTest {
        server.respond("/Users/user-1/Items/202", "album_with_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/107").copy(serverAlbumId = "202", artworkItemId = "105"))
        provider.getAlbumArtworkUrl(song("emby://item/108").copy(serverAlbumId = "202", artworkItemId = "105"))

        server.requestsTo("/Users/user-1/Items/202").size shouldBe 1
    }

    @Test
    fun `a song borrowing a sibling's image shows it when the server doesn't know the album`() = runTest {
        provider.getAlbumArtworkUrl(song("emby://item/107").copy(artworkItemId = "105")) shouldBe "${server.address}/Items/105/Images/Primary?maxWidth=1000&maxHeight=1000"
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `a song with neither an album nor an image of its own has no artwork`() = runTest {
        server.respond("/Users/user-1/Items/104", "loose_item.json")

        provider.getAlbumArtworkUrl(song("emby://item/104")) shouldBe null
        server.requestsTo("/Users/user-1/Items/104").size shouldBe 1
    }

    @Test
    fun `an album image wins over the song's own primary image`() = runTest {
        server.respond("/Users/user-1/Items/106", "item_album_and_own_image.json")

        provider.getAlbumArtworkUrl(song("emby://item/106")) shouldBe "${server.address}/Items/202/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `artist artwork is the named artist's primary image - without asking for the song`() = runTest {
        provider.getArtistArtworkUrl(song("emby://item/102"), serverArtistId = "301") shouldBe "${server.address}/Items/301/Images/Primary?maxWidth=1000&maxHeight=1000"
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `a song without an album has no album artwork`() = runTest {
        server.respond("/Users/user-1/Items/104", "loose_item.json")

        provider.getAlbumArtworkUrl(song("emby://item/104")) shouldBe null
    }

    @Test
    fun `no artwork when the server can't find the song`() = runTest {
        provider.getAlbumArtworkUrl(song("emby://item/102")) shouldBe null
    }

    @Test
    fun `no artwork for a path without an item id - without asking the server`() = runTest {
        provider.getAlbumArtworkUrl(song("emby://item")) shouldBe null
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no artwork when signed out`() = runTest {
        credentialStore.authenticatedCredentials = null

        provider.getAlbumArtworkUrl(song("emby://item/102")) shouldBe null
        provider.getArtistArtworkUrl(song("emby://item/102"), serverArtistId = "301") shouldBe null
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
        mediaProvider = MediaProviderType.Emby,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
