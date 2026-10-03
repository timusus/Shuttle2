package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
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
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.Url
import kotlin.test.Test

/** Direct play vs transcode for a Plex song, on Android's profile (HLS, no ALAC) and iOS's (progressive MP3, ALAC). */
class PlexStreamUrlProviderTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = credentials
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(createHttpClient(FixtureServer { error("not called") }.engine)),
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false
    private val bitrateCap = StreamingBitrateCap(streamingSettings) { metered }

    private val android = PlexStreamUrlProvider(authenticationManager, bitrateCap, StreamProfile.Android)
    private val ios = PlexStreamUrlProvider(authenticationManager, bitrateCap, StreamProfile.Ios)

    @Test
    fun `handles plex songs only`() {
        android.handles("plex") shouldBe true
        android.handles("jellyfin") shouldBe false
    }

    @Test
    fun `stream fails when not signed in`() {
        credentialStore.authenticatedCredentials = null

        runCatching { ios.streamUrl(song(externalId = PART)) }.exceptionOrNull()?.message shouldBe "Failed to authenticate"
    }

    @Test
    fun `stream is the original part file when there's no cap`() {
        val stream = android.stream(song(externalId = PART, bitRate = 1_411))

        stream.path shouldStartWith "http://plex.local:32400$PART?"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `stream is the original part file when its bitrate is within the cap`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        android.stream(song(externalId = PART, bitRate = 256)).path shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `stream is an HLS transcode at the cap when the song is over it`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192

        val stream = android.stream(song(externalId = PART, bitRate = 1_411))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=192&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream transcodes a song of unknown bitrate`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        android.stream(song(externalId = PART, bitRate = null)).path shouldContain "/transcode/universal/start.m3u8?"
    }

    @Test
    fun `stream uses the metered cap on a metered network`() {
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        android.stream(song(externalId = PART, bitRate = 320)).path shouldContain "&musicBitrate=128&"
    }

    @Test
    fun `stream transcodes a format the player can't decode - with no cap - issue 362`() {
        val stream = android.stream(song(externalId = "/library/parts/43/1600000000/file.wma", bitRate = 128))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream transcodes a format the player can't decode even within the cap`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192

        val stream = android.stream(song(externalId = "/library/parts/44/1600000000/file.aiff", bitRate = 128))

        stream.path shouldContain "/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=192&"
    }

    @Test
    fun `stream is the original part file for each format the player decodes`() {
        listOf("mp3", "m4a", "mp4", "flac", "ogg", "opus", "wav").forEach { extension ->
            val part = "/library/parts/45/1600000000/file.$extension"
            android.stream(song(externalId = part, bitRate = 256)).path shouldStartWith "http://plex.local:32400$part?"
        }
    }

    @Test
    fun `stream transcodes ALAC even inside a container the player otherwise decodes - issue 567`() {
        val stream = android.stream(song(externalId = "/library/parts/46/1600000000/file.m4a", bitRate = 1_000, audioCodec = "alac"))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.m3u8?"
        stream.path shouldContain "&musicBitrate=320&"
        stream.mimeType shouldBe "application/x-mpegURL"
    }

    @Test
    fun `stream plays a song whose codec is known and decodable`() {
        android.stream(song(externalId = PART, bitRate = 1_000, audioCodec = "flac")).path shouldStartWith "http://plex.local:32400$PART?"
    }

    @Test
    fun `iOS plays ALAC and AIFF as they are - its decoder has them`() {
        listOf("/library/parts/46/1600000000/file.m4a" to "alac", "/library/parts/44/1600000000/file.aiff" to null).forEach { (part, codec) ->
            ios.streamUrl(song(externalId = part, bitRate = 1_000, audioCodec = codec)) shouldStartWith "http://plex.local:32400$part?"
        }
    }

    @Test
    fun `iOS transcodes to one progressive MP3 - its engine has no HLS`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192

        val stream = ios.stream(song(externalId = PART, bitRate = 1_411))

        stream.path shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        stream.path shouldContain "&protocol=http&"
        stream.path shouldContain "&musicBitrate=192&"
        stream.path shouldNotContain "offset="
        stream.mimeType shouldBe "audio/mpeg"
        Url(stream.path).parameters["X-Plex-Client-Profile-Extra"] shouldBe
            "add-transcode-target(type=musicProfile&context=streaming&protocol=http&container=mp3&audioCodec=mp3)"
    }

    @Test
    fun `iOS transcodes a format its player can't decode at the uncapped bitrate`() {
        ios.streamUrl(song(externalId = "/library/parts/43/1600000000/file.wma", bitRate = 128)) shouldContain "&musicBitrate=320&"
    }

    @Test
    fun `an iOS transcode starts at the position asked for - in seconds`() {
        val url = Url(ios.streamUrl(song(externalId = "/library/parts/43/1600000000/file.wma"), startPositionMs = 83_045))

        url.parameters["offset"] shouldBe "83.045"
    }

    @Test
    fun `each iOS transcode is its own session - so opening the next song doesn't end this one`() {
        val first = Url(ios.streamUrl(song(externalId = "/library/parts/43/1600000000/file.wma")))
        val second = Url(ios.streamUrl(song(externalId = "/library/parts/43/1600000000/file.wma")))

        first.parameters["session"] shouldBe first.parameters["X-Plex-Session-Identifier"]
        (first.parameters["session"] == second.parameters["session"]) shouldBe false
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
