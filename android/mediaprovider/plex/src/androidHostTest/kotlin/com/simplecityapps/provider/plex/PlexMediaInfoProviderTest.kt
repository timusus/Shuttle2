package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * Exercises [PlexMediaInfoProvider] itself (not just [PlexAuthenticationManager.buildPlexPath],
 * already covered by [PlexAuthenticationTest]). Asserts on [PlexMediaInfoProvider.buildDownloadStream]
 * rather than [PlexMediaInfoProvider.downloadInfo] directly, since the latter calls `String.toUri()`,
 * which needs a mocked `android.net.Uri` and would pull Robolectric into this module for nothing else.
 */
class PlexMediaInfoProviderTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = "http://plex.local:32400"
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(createHttpClient(FixtureServer { error("not called") }.engine)),
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false

    private val provider = PlexMediaInfoProvider(authenticationManager, StreamingBitrateCap(streamingSettings) { metered })

    @Test
    fun `download stream is the same original part-file url used for streaming - with the song's mime type`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        val song = song(externalId = "/library/parts/42/file.mp3")

        val stream = provider.buildDownloadStream(song)!!

        stream.path shouldBe "http://plex.local:32400/library/parts/42/file.mp3" +
            "?X-Plex-Token=token123" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
            "&X-Plex-Device=Android"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `download stream is null when not authenticated`() = runTest {
        val song = song(externalId = "/library/parts/42/file.mp3")

        provider.buildDownloadStream(song) shouldBe null
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
    fun `stream transcodes a format the player can't decode - with no cap - issue 362`() {
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
    fun `stream transcodes ALAC even inside a container the player otherwise decodes - issue 567`() {
        credentialStore.authenticatedCredentials = credentials

        val stream = provider.buildStream(song(externalId = "/library/parts/46/1600000000/file.m4a", bitRate = 1_000, audioCodec = "alac"))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream plays a song whose codec is known and decodable`() {
        credentialStore.authenticatedCredentials = credentials

        provider.buildStream(song(externalId = PART, bitRate = 1_000, audioCodec = "flac")).path shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `download stream stays the original part file under a cap`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        provider.buildDownloadStream(song(externalId = PART, bitRate = 1_411))!!.path shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `download stream transcodes a format the player can't decode into a single playable file - not HLS - issue 567`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        val stream = provider.buildDownloadStream(song(externalId = "/library/parts/47/1600000000/file.wma", bitRate = 128))!!

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        stream.path shouldContain "&protocol=http&"
        stream.path shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `download stream transcodes ALAC even inside a container the player otherwise decodes - issue 567`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        val stream = provider.buildDownloadStream(song(externalId = "/library/parts/48/1600000000/file.m4a", bitRate = 1_000, audioCodec = "alac"))!!

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        stream.path shouldContain "&protocol=http&"
        stream.mimeType shouldBe "audio/mpeg"
    }

    private fun song(
        externalId: String?,
        bitRate: Int? = null,
        audioCodec: String? = null
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
        channelCount = null,
        audioCodec = audioCodec
    )

    private companion object {
        const val PART = "/library/parts/42/file.flac"
    }
}
