package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.User
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Exercises [EmbyMediaInfoProvider.downloadFallbackUri]'s 401-vs-403 handling (#322): a 403 means
 * the server actually revoked download permission, so it's persisted; a 401 can also mean the
 * cached session expired, so it isn't. Asserts on [EmbyMediaInfoProvider.buildFallbackPathString]
 * rather than [EmbyMediaInfoProvider.downloadFallbackUri] directly, since the latter calls
 * `String.toUri()`, which needs a mocked `android.net.Uri` and would pull Robolectric into this
 * module for nothing else. The stream URL's bitrate cap (#504) is asserted on
 * [EmbyMediaInfoProvider.buildPlaybackPathString] for the same reason.
 */
class EmbyMediaInfoProviderTest {
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://emby.local:8096"
    }

    private val authenticationManager = EmbyAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                body: Map<String, String>,
                header: String
            ): NetworkResult<AuthenticationResult> = error("not called")

            override suspend fun meImpl(
                url: String,
                token: String
            ): NetworkResult<User> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
    )

    private val streamingSettings = StreamingSettings(SettingsStore(FakeSharedPreferences()))
    private var metered = false

    private val provider = EmbyMediaInfoProvider(
        authenticationManager,
        object : EmbyTranscodeService {
            override suspend fun transcode(url: String) = error("not called")
        },
        StreamingBitrateCap(streamingSettings) { metered }
    )

    @Test
    fun `a 403 persists that download permission is disabled`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 403) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }

    @Test
    fun `a 401 leaves the stored download permission alone`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 401) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `fallback path is the static stream url`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 403) }!!

        path shouldBe "http://emby.local:8096/emby/Audio/item789/stream?static=true&api_key=token123"
    }

    @Test
    fun `stream url sends no bitrate cap for original quality, so the server direct-plays`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = provider.buildPlaybackPathString(song())

        path shouldContain "http://emby.local:8096/emby/Audio/item789/universal?"
        path shouldNotContain "MaxStreamingBitrate"
    }

    @Test
    fun `stream url caps the bitrate in bits per second on an unmetered network`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        val path = provider.buildPlaybackPathString(song())

        path shouldContain "&MaxStreamingBitrate=320000&"
        path shouldContain "&TranscodingProtocol=hls"
        path shouldEndWith "&api_key=token123"
    }

    @Test
    fun `stream url uses the metered cap on a metered network`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Original
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        provider.buildPlaybackPathString(song()) shouldContain "&MaxStreamingBitrate=128000&"
    }

    @Test
    fun `the cap never reaches the download or static stream urls`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        authenticationManager.buildDownloadPath("item789", downloadableCredentials)!! shouldNotContain "MaxStreamingBitrate"
        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 401) }!! shouldNotContain "MaxStreamingBitrate"
    }

    private fun song() = Song(
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
        path = "emby://item/item789",
        size = 0,
        mimeType = "Audio/*",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        externalId = null,
        mediaProvider = MediaProviderType.Emby,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
