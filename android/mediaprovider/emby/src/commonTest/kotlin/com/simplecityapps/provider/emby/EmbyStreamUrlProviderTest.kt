package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

/** A Emby song's stream URL: direct play at original quality, else capped by the active network's quality (#504). */
class EmbyStreamUrlProviderTest {
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
        address = "http://emby.local:8096"
    }

    private val authenticationManager = EmbyAuthenticationManager(
        userService = UserService(createHttpClient(FixtureServer("emby").engine)),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
        streamProfile = StreamProfile.Android
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false

    private val provider = EmbyStreamUrlProvider(authenticationManager, StreamingBitrateCap(streamingSettings) { metered })

    @Test
    fun `handles only emby paths`() {
        provider.handles("emby") shouldBe true
        provider.handles("file") shouldBe false
        provider.handles(null) shouldBe false
    }

    @Test
    fun `stream url sends no bitrate cap for original quality - so the server direct-plays`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = provider.streamUrl(song())

        path shouldContain "http://emby.local:8096/emby/Audio/item789/universal?"
        path shouldNotContain "MaxStreamingBitrate"
    }

    @Test
    fun `stream url caps the bitrate in bits per second on an unmetered network`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        val path = provider.streamUrl(song())

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

        provider.streamUrl(song()) shouldContain "&MaxStreamingBitrate=128000&"
    }

    @Test
    fun `a signed-out server fails the stream`() {
        shouldThrow<IllegalStateException> { provider.streamUrl(song()) }
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
