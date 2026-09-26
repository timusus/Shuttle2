package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

/** Artwork urls for Emby songs, which have no image when the server doesn't know the song's album or artist (#525). */
@RunWith(RobolectricTestRunner::class)
class EmbyRemoteArtworkProviderTest {
    private val server = EmbyServer()

    private val retrofit =
        Retrofit.Builder()
            .baseUrl("${server.address}/")
            .addCallAdapterFactory(NetworkResultAdapterFactory(null))
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
            .build()

    private val credentialStore =
        CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token-1", userId = "user-1")
        }

    private val provider =
        EmbyRemoteArtworkProvider(
            embyAuthenticationManager =
                EmbyAuthenticationManager(
                    userService = retrofit.create(),
                    credentialStore = credentialStore,
                    clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.26", deviceName = "Pixel")
                ),
            credentialStore = credentialStore,
            itemsService = retrofit.create()
        )

    @After
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
    fun `artist artwork is the first artist's primary image`() = runTest {
        server.respond("/Users/user-1/Items/102", "item.json")

        provider.getArtistArtworkUrl(song("emby://item/102")) shouldBe "${server.address}/Items/301/Images/Primary?maxWidth=1000&maxHeight=1000"
    }

    @Test
    fun `a song without an album has no album artwork`() = runTest {
        server.respond("/Users/user-1/Items/104", "loose_item.json")

        provider.getAlbumArtworkUrl(song("emby://item/104")) shouldBe null
    }

    @Test
    fun `a song without artists has no artist artwork`() = runTest {
        server.respond("/Users/user-1/Items/104", "loose_item.json")

        provider.getArtistArtworkUrl(song("emby://item/104")) shouldBe null
    }

    @Test
    fun `no artwork when the server can't find the song`() = runTest {
        provider.getAlbumArtworkUrl(song("emby://item/102")) shouldBe null
        provider.getArtistArtworkUrl(song("emby://item/102")) shouldBe null
    }

    @Test
    fun `no artwork for a path without an item id, without asking the server`() = runTest {
        provider.getAlbumArtworkUrl(song("emby://item")) shouldBe null
        provider.getArtistArtworkUrl(song("emby://item")) shouldBe null
        server.requests.shouldBeEmpty()
    }

    @Test
    fun `no artwork when signed out`() = runTest {
        credentialStore.authenticatedCredentials = null

        provider.getAlbumArtworkUrl(song("emby://item/102")) shouldBe null
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
