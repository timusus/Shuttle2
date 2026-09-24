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

/**
 * Exercises [PlexMediaInfoProvider] itself (not just [PlexAuthenticationManager.buildPlexPath],
 * already covered by [PlexAuthenticationTest]). Asserts on [PlexMediaInfoProvider.buildDownloadPathString]
 * rather than [PlexMediaInfoProvider.downloadUri] directly, since the latter calls `String.toUri()`,
 * which needs a mocked `android.net.Uri` and would pull Robolectric into this module for nothing else.
 */
class PlexMediaInfoProviderTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://plex.local:32400"
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
    fun `download path is the same original part-file url used for streaming`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        val song = song(externalId = "/library/parts/42/file.mp3")

        val path = provider.buildDownloadPathString(song)!!

        path shouldBe "http://plex.local:32400/library/parts/42/file.mp3" +
            "?X-Plex-Token=token123" +
            "&X-Plex-Client-Identifier=s2-music-payer" +
            "&X-Plex-Device=Android"
    }

    @Test
    fun `download path is null when not authenticated`() = runTest {
        val song = song(externalId = "/library/parts/42/file.mp3")

        provider.buildDownloadPathString(song) shouldBe null
    }

    @Test
    fun `downloadFallbackUri is always null since plex has no separate download permission`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        provider.downloadFallbackUri("plex://item/107898", 403) shouldBe null
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
