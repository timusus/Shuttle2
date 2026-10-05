package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingPolicy
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
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
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

    private val deliveredFormats = DeliveredFormats()

    private val provider = EmbyStreamUrlProvider(authenticationManager, StreamingPolicy(streamingSettings, deliveredFormats) { metered })

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
    fun `Auto transcodes to AAC in HLS segments on Android`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = provider.streamUrl(song())

        path shouldContain "&TranscodingContainer=ts&TranscodingProtocol=hls&"
        path shouldContain "&AudioCodec=aac&"
    }

    @Test
    fun `the chosen codec is what a stream transcodes to`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        streamingSettings.transcodeFormat.value = TranscodeFormat.Mp3
        provider.streamUrl(song()) shouldContain "&TranscodingContainer=ts&TranscodingProtocol=hls&"
        provider.streamUrl(song()) shouldContain "&AudioCodec=mp3&"
    }

    @Test
    fun `Opus streams transcode to AAC in HLS segments on Android`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.transcodeFormat.value = TranscodeFormat.Opus

        val path = provider.streamUrl(song())

        path shouldContain "&TranscodingContainer=ts&TranscodingProtocol=hls&"
        path shouldContain "&AudioCodec=aac&"
    }

    @Test
    fun `a song over the cap is reported as a transcode at the cap`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        provider.streamUrl(song(bitRate = 900))

        deliveredFormats.byPath.value[SONG_PATH] shouldBe DeliveredFormat("AAC", 320)
    }

    @Test
    fun `a decodable song within the cap is reported as the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        provider.streamUrl(song(bitRate = 256, audioCodec = "aac"))

        deliveredFormats.byPath.value[SONG_PATH] shouldBe null
    }

    @Test
    fun `a song the player can't decode is reported as a transcode - though within the cap`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        provider.streamUrl(song(bitRate = 256, audioCodec = "alac"))

        deliveredFormats.byPath.value[SONG_PATH] shouldBe DeliveredFormat("AAC", null)
    }

    @Test
    fun `a download keeps the original at Original download quality`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.meteredQuality.value = StreamingQuality.Kbps128
        metered = true

        val source = provider.downloadSource(song(bitRate = 900))!!

        source.url shouldBe "http://emby.local:8096/emby/Items/item789/Download?api_key=token123"
        source.mimeType shouldBe "Audio/*"
    }

    @Test
    fun `a download over the download cap is a progressive transcode in the chosen codec`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192
        streamingSettings.transcodeFormat.value = TranscodeFormat.Opus

        val source = provider.downloadSource(song(bitRate = 900))!!

        source.url shouldContain "/emby/Audio/item789/universal?"
        source.url shouldContain "&Container=ogg|opus&TranscodingContainer=ogg&TranscodingProtocol=http&"
        source.url shouldContain "&AudioCodec=opus&MaxStreamingBitrate=192000&"
        source.mimeType shouldBe "audio/ogg"
    }

    @Test
    fun `Auto downloads transcode to a single AAC file on Android`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps128

        val source = provider.downloadSource(song(bitRate = null))!!

        source.url shouldContain "&TranscodingContainer=aac&TranscodingProtocol=http&"
        source.mimeType shouldBe "audio/aac"
    }

    @Test
    fun `a download within the download cap keeps the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192

        provider.downloadSource(song(bitRate = 128))!!.url shouldContain "/Items/item789/Download?"
    }

    @Test
    fun `an ALAC download with no cap is a transcode because the player can't decode the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val source = provider.downloadSource(song(bitRate = 900, audioCodec = "alac"))!!

        source.url shouldContain "/emby/Audio/item789/universal?"
        source.url shouldNotContain "MaxStreamingBitrate"
        source.url shouldNotContain "/Download?"
    }

    @Test
    fun `AAC and FLAC downloads with no cap keep the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        provider.downloadSource(song(bitRate = 900, audioCodec = "aac"))!!.url shouldContain "/Items/item789/Download?"
        provider.downloadSource(song(bitRate = 900, audioCodec = "flac"))!!.url shouldContain "/Items/item789/Download?"
    }

    @Test
    fun `an ALAC download is a transcode even with a cap above its bitrate`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192

        val source = provider.downloadSource(song(bitRate = 100, audioCodec = "alac"))!!

        source.url shouldContain "/emby/Audio/item789/universal?"
        source.url shouldContain "&MaxStreamingBitrate=192000&"
        source.mimeType shouldBe "audio/aac"
    }

    @Test
    fun `a download of unknown codec with no cap keeps the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val source = provider.downloadSource(song(bitRate = null, audioCodec = null))!!

        source.url shouldBe "http://emby.local:8096/emby/Items/item789/Download?api_key=token123"
        source.mimeType shouldBe "Audio/*"
    }

    @Test
    fun `an ALAC fallback is the same progressive transcode as the download and not the original`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val fallback = provider.downloadFallback(song(bitRate = 900, audioCodec = "alac"), 401)!!

        fallback shouldBe provider.downloadSource(song(bitRate = 900, audioCodec = "alac"))!!.copy(url = fallback.url)
        fallback.url shouldContain "&TranscodingContainer=aac&TranscodingProtocol=http&"
        fallback.mimeType shouldBe "audio/aac"
    }

    @Test
    fun `a 403 on an ALAC transcode leaves the stored download permission alone`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        provider.downloadFallback(song(audioCodec = "alac"), 403)

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `a fallback for a decodable song is the static stream url recorded as its own type`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val fallback = provider.downloadFallback(song(audioCodec = "flac"), 401)!!

        fallback.url shouldBe "http://emby.local:8096/emby/Audio/item789/stream?static=true&api_key=token123"
        fallback.mimeType shouldBe "Audio/*"
    }

    @Test
    fun `a 403 persists that download permission is disabled`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        provider.downloadFallback(song(audioCodec = "flac"), 403)

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }

    @Test
    fun `a 401 leaves the stored download permission alone`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        provider.downloadFallback(song(audioCodec = "flac"), 401)

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `the cap never reaches the static stream fallback`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        provider.downloadFallback(song(audioCodec = "flac"), 401)!!.url shouldNotContain "MaxStreamingBitrate"
    }

    @Test
    fun `a signed-out server has no fallback`() {
        provider.downloadFallback(song(audioCodec = "flac"), 401) shouldBe null
    }

    @Test
    fun `a signed-out server fails the stream`() {
        shouldThrow<IllegalStateException> { provider.streamUrl(song()) }
    }

    private fun song(
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
        bitRate = bitRate,
        bitDepth = null,
        sampleRate = null,
        channelCount = null,
        audioCodec = audioCodec
    )

    private companion object {
        const val SONG_PATH = "emby://item/item789"
    }
}
