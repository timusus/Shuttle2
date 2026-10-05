package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.TranscodeService
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
 * Exercises [PlexMediaInfoProvider]'s downloads (its streams are [PlexStreamUrlProvider]'s, covered by
 * [PlexStreamUrlProviderTest]). Asserts on [PlexStreamUrlProvider.downloadSource], which [PlexMediaInfoProvider.downloadInfo]
 * wraps, rather than on `downloadInfo` directly, since that calls `String.toUri()`, which needs a mocked
 * `android.net.Uri` and would pull Robolectric into this module for nothing else.
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

    private val streamUrls = PlexStreamUrlProvider(
        authenticationManager,
        StreamingPolicy(streamingSettings) { metered },
        StreamProfile.Android,
        TranscodeService(createHttpClient(FixtureServer { error("not called") }.engine))
    )

    private val provider = PlexMediaInfoProvider(streamUrls)

    @Test
    fun `download stream is the same original part-file url used for streaming - with the song's mime type`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        val song = song(externalId = "/library/parts/42/file.mp3")

        val stream = streamUrls.downloadSource(song)!!

        stream.url shouldBe "http://plex.local:32400/library/parts/42/file.mp3" +
            "?X-Plex-Token=token123" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
            "&X-Plex-Device=Android"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `download stream is null when not authenticated`() = runTest {
        val song = song(externalId = "/library/parts/42/file.mp3")

        streamUrls.downloadSource(song) shouldBe null
    }

    @Test
    fun `downloadFallbackUri is always null since plex has no separate download permission`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        provider.downloadFallbackUri("plex://item/107898", 403) shouldBe null
    }

    @Test
    fun `download stream stays the original part file under a cap`() = runTest {
        credentialStore.authenticatedCredentials = credentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        streamUrls.downloadSource(song(externalId = PART, bitRate = 1_411))!!.url shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `download stream transcodes a format the player can't decode into a single playable file - not HLS - issue 567`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        val stream = streamUrls.downloadSource(song(externalId = "/library/parts/47/1600000000/file.wma", bitRate = 128))!!

        stream.url shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        stream.url shouldContain "&protocol=http&"
        stream.url shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `download stream transcodes ALAC even inside a container the player otherwise decodes - issue 567`() = runTest {
        credentialStore.authenticatedCredentials = credentials

        val stream = streamUrls.downloadSource(song(externalId = "/library/parts/48/1600000000/file.m4a", bitRate = 1_000, audioCodec = "alac"))!!

        stream.url shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        stream.url shouldContain "&protocol=http&"
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
