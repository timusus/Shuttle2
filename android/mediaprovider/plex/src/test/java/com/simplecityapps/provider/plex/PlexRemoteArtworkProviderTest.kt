package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.MediaContainer
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.QueryResult
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlexRemoteArtworkProviderTest {
    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    private val authenticationManager = PlexAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                login: String,
                password: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")
    )

    private val service = FakeItemsService()
    private val provider = PlexRemoteArtworkProvider(authenticationManager, service)

    private val song = song(path = "plex:///library/metadata/107898")

    @Test
    fun `album artwork url is the item's parent thumb, without the plex token`() = runTest {
        service.metadata = metadata(parentThumb = "/library/metadata/107898/thumb/1700000000")

        provider.getAlbumArtworkUrl(song) shouldBe
            "http://plex.local:32400/library/metadata/107898/thumb/1700000000"

        service.requestedKey shouldBe "/library/metadata/107898"
    }

    @Test
    fun `album artwork falls back to the item's own thumb when it has no parent thumb`() = runTest {
        service.metadata = metadata(parentThumb = null, thumb = "/library/metadata/107898/thumb/1700000000")

        provider.getAlbumArtworkUrl(song) shouldBe
            "http://plex.local:32400/library/metadata/107898/thumb/1700000000"
    }

    @Test
    fun `artist artwork url is the item's grandparent thumb`() = runTest {
        service.metadata = metadata(grandparentThumb = "/library/metadata/1/thumb/1700000000")

        provider.getArtistArtworkUrl(song) shouldBe
            "http://plex.local:32400/library/metadata/1/thumb/1700000000"
    }

    @Test
    fun `no artwork url when the item carries no thumb`() = runTest {
        service.metadata = metadata(parentThumb = null, thumb = null)

        provider.getAlbumArtworkUrl(song) shouldBe null
    }

    @Test
    fun `no artwork url when the server request fails`() = runTest {
        service.failure = RuntimeException("boom")

        provider.getAlbumArtworkUrl(song) shouldBe null
    }

    @Test
    fun `no artwork url for a song whose path carries no metadata key`() = runTest {
        provider.getAlbumArtworkUrl(song(path = "plex://item/107898")) shouldBe null
    }

    @Test
    fun `no artwork url when not authenticated`() = runTest {
        credentialStore.authenticatedCredentials = null

        provider.getAlbumArtworkUrl(song) shouldBe null
    }

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

    private class FakeItemsService : ItemsService {
        var metadata: Metadata? = null
        var failure: Throwable? = null
        var requestedKey: String? = null

        override suspend fun itemsImpl(
            url: String,
            token: String
        ): NetworkResult<QueryResult> {
            requestedKey = url.removePrefix("http://plex.local:32400")
            failure?.let { return NetworkResult.Failure(it) }
            return NetworkResult.Success(QueryResult(MediaContainer(metadata = listOfNotNull(metadata), directories = null)))
        }
    }
}
