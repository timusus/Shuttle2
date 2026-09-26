package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
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

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = PlexAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                login: String,
                password: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    private val streamingSettings = StreamingSettings(SettingsStore(FakeSharedPreferences()))
    private var metered = false

    private val provider = PlexMediaInfoProvider(authenticationManager, StreamingBitrateCap(streamingSettings) { metered })

    @Test
    fun `download path is the same original part-file url used for streaming`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        val song = song(externalId = "/library/parts/42/file.mp3")

        val path = provider.buildDownloadPathString(song)!!

        path shouldBe "http://plex.local:32400/library/parts/42/file.mp3" +
            "?X-Plex-Token=token123" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
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

    @Test
    fun `stream is the original part file when there's no cap`() {
        credentialStore.authenticatedCredentials = credentials

        val stream = provider.buildStream(song(externalId = PART, bitRate = 1_411))

        stream.path shouldStartWith "http://plex.local:32400$PART?"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `stream is the original part file when its bitrate is within the cap`() {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        provider.buildStream(song(externalId = PART, bitRate = 256)).path shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `stream is an HLS transcode at the cap when the song is over it`() {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192

        val stream = provider.buildStream(song(externalId = PART, bitRate = 1_411))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=192&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream transcodes a song of unknown bitrate`() {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        provider.buildStream(song(externalId = PART, bitRate = null)).path shouldContain "/transcode/universal/start.m3u8?"
    }

    @Test
    fun `stream uses the metered cap on a metered network`() {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        provider.buildStream(song(externalId = PART, bitRate = 320)).path shouldContain "&musicBitrate=128&"
    }

    @Test
    fun `stream transcodes a format the player can't decode, with no cap (#362)`() {
        credentialStore.authenticatedCredentials = credentials

        val stream = provider.buildStream(song(externalId = "/library/parts/43/1600000000/file.wma", bitRate = 128))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream transcodes a format the player can't decode even within the cap`() {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192

        val stream = provider.buildStream(song(externalId = "/library/parts/44/1600000000/file.aiff", bitRate = 128))

        stream.path shouldContain "/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=192&"
    }

    @Test
    fun `stream is the original part file for each format the player decodes`() {
        credentialStore.authenticatedCredentials = credentials

        listOf("mp3", "m4a", "mp4", "flac", "ogg", "opus", "wav").forEach { extension ->
            val part = "/library/parts/45/1600000000/file.$extension"
            provider.buildStream(song(externalId = part, bitRate = 256)).path shouldStartWith "http://plex.local:32400$part?"
        }
    }

    @Test
    fun `download path stays the original part file under a cap`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        provider.buildDownloadPathString(song(externalId = PART, bitRate = 1_411))!! shouldStartWith "http://plex.local:32400$PART?"
    }

    private fun song(
        externalId: String?,
        bitRate: Int? = null
    ) = Song(
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
        path = "plex:///library/metadata/107898",
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
        bitRate = bitRate,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private companion object {
        const val PART = "/library/parts/42/file.flac"
    }
}
