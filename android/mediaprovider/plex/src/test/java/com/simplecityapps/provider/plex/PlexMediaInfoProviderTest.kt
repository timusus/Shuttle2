package com.simplecityapps.provider.plex

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlexMediaInfoProviderTest {
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
        credentialStore = credentialStore
    )
    private val provider = PlexMediaInfoProvider(authenticationManager)

    @Test
    fun `download uri is the same original part-file url used for streaming`() = runTest {
        val song = song(externalId = "/library/parts/42/file.mp3")

        provider.downloadUri(song) shouldBe provider.getMediaInfo(song).path
    }

    private fun song(externalId: String?) = Song(
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
        path = "plex://item/107898",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = externalId,
        mediaProvider = MediaProviderType.Plex,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
