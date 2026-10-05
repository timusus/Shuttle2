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
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Direct play vs transcode for a Plex song, on Android's profile (HLS, no ALAC) and iOS's (progressive MP3, ALAC). */
class PlexStreamUrlProviderTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = credentials
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val server = FixtureServer { error("not called") }

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(createHttpClient(server.engine)),
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false
    private val streamingPolicy = StreamingPolicy(streamingSettings, DeliveredFormats()) { metered }

    private val transcodeService = TranscodeService(createHttpClient(server.engine))

    private val android = PlexStreamUrlProvider(authenticationManager, streamingPolicy, StreamProfile.Android, transcodeService)
    private val ios = PlexStreamUrlProvider(authenticationManager, streamingPolicy, StreamProfile.Ios, transcodeService)

    @AfterTest
    fun tearDown() {
        server.close()
    }

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
        stream.isTranscode shouldBe false
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
        stream.isTranscode shouldBe true
    }

    @Test
    fun `an HLS transcode is in the chosen codec - AAC for Auto`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192
        val flac = song(externalId = PART, bitRate = 1_411)

        Url(android.stream(flac).path).parameters["X-Plex-Client-Profile-Extra"]!! shouldContain "audioCodec=aac"
        streamingSettings.transcodeFormat.value = TranscodeFormat.Mp3
        Url(android.stream(flac).path).parameters["X-Plex-Client-Profile-Extra"]!! shouldContain "container=mpegts&audioCodec=mp3"
    }

    @Test
    fun `an HLS transcode falls back to AAC for Opus - which MPEG-TS can't carry`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps192
        streamingSettings.transcodeFormat.value = TranscodeFormat.Opus

        Url(android.stream(song(externalId = PART, bitRate = 1_411)).path).parameters["X-Plex-Client-Profile-Extra"]!! shouldContain "audioCodec=aac"
    }

    @Test
    fun `a download keeps the original part file at Original download quality - whatever the stream cap`() {
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        val source = android.downloadSource(song(externalId = PART, bitRate = 1_411))!!

        source.url shouldNotContain "/transcode/"
    }

    @Test
    fun `a download over the download cap is a progressive MP3 transcode at the cap`() {
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192

        val source = android.downloadSource(song(externalId = PART, bitRate = 1_411))!!

        source.url shouldStartWith "http://plex.local:32400/music/:/transcode/universal/start.mp3?"
        source.url shouldContain "&musicBitrate=192&"
        source.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `a download within the download cap keeps the original part file`() {
        streamingSettings.downloadQuality.value = StreamingQuality.Kbps192

        android.downloadSource(song(externalId = PART, bitRate = 128))!!.url shouldNotContain "/transcode/"
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
        stream.isTranscode shouldBe true
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
    fun `every transcode of a song carries one session identifier - as Plex answers 400 to another for a track just played`() {
        val streams = listOf(
            ios.streamUrl(song(externalId = WMA), playId = "play-1"),
            ios.streamUrl(song(externalId = WMA), playId = "play-2"),
            ios.streamUrl(song(externalId = WMA)),
            android.stream(song(externalId = WMA)).path,
            ios.downloadSource(song(externalId = WMA))!!.url
        ).map(::Url)

        streams.map { it.parameters["X-Plex-Session-Identifier"] }.toSet() shouldBe setOf("s2-107898")
    }

    @Test
    fun `plays of a song share a transcode session while one holds it - so repeat one's next joins the transcode playing`() {
        val playing = Url(ios.streamUrl(song(externalId = WMA), playId = "play-1"))
        val upNext = Url(ios.streamUrl(song(externalId = WMA), playId = "play-2"))

        playing.parameters["session"] shouldBe "s2-107898-1"
        upNext.parameters["session"] shouldBe "s2-107898-1"
    }

    @Test
    fun `a stream with no play is on the song's own session - which ending a play never stops`() = runTest {
        server.respond(TRANSCODE_STOP)
        val hls = Url(android.stream(song(externalId = WMA)).path)
        ios.streamUrl(song(externalId = WMA), playId = "play-1")

        ios.endPlay("play-1")

        hls.parameters["session"] shouldBe "s2-107898"
        server.requestsTo(TRANSCODE_STOP).single().url.parameters["session"] shouldBe "s2-107898-1"
    }

    @Test
    fun `an iOS play keeps its transcode session across the re-opens its seeks make`() {
        val opened = ios.streamUrl(song(externalId = WMA), playId = "play-1")
        val reopened = Url(ios.streamUrl(song(externalId = WMA), startPositionMs = 83_045, playId = "play-1"))

        reopened.parameters["session"] shouldBe Url(opened).parameters["session"]
        ios.streamUrl(song(externalId = WMA), playId = "play-1") shouldBe opened
    }

    @Test
    fun `ending a play stops its transcode session once`() = runTest {
        server.respond(TRANSCODE_STOP)
        ios.streamUrl(song(externalId = WMA), playId = "play-1")

        ios.endPlay("play-1")
        ios.endPlay("play-1")

        val stop = server.requestsTo(TRANSCODE_STOP).single()
        stop.url.parameters["session"] shouldBe "s2-107898-1"
        stop.headers["X-Plex-Token"] shouldBe "token123"
    }

    @Test
    fun `ending a play leaves the session running while another play of the song holds it`() = runTest {
        server.respond(TRANSCODE_STOP)
        ios.streamUrl(song(externalId = WMA), playId = "play-1")
        ios.streamUrl(song(externalId = WMA), playId = "play-2")

        ios.endPlay("play-1")
        server.requestsTo(TRANSCODE_STOP).shouldBeEmpty()

        ios.endPlay("play-2")
        server.requestsTo(TRANSCODE_STOP).single().url.parameters["session"] shouldBe "s2-107898-1"
    }

    @Test
    fun `a play opened once the song's last play has ended gets a new session - which that play's stop can't reach`() = runTest {
        server.respond(TRANSCODE_STOP)
        ios.streamUrl(song(externalId = WMA), playId = "play-1")
        ios.endPlay("play-1")

        val replay = Url(ios.streamUrl(song(externalId = WMA), playId = "play-2"))

        replay.parameters["session"] shouldBe "s2-107898-2"
        replay.parameters["X-Plex-Session-Identifier"] shouldBe "s2-107898"
    }

    @Test
    fun `a play opened while the song's last play ends is never on the session that's stopped`() = runTest {
        server.respond(TRANSCODE_STOP)
        var ending = "play-0"
        ios.streamUrl(song(externalId = WMA), playId = ending)
        repeat(200) { i ->
            val opening = "play-${i + 1}"
            val session = withContext(Dispatchers.Default) {
                val opened = async { Url(ios.streamUrl(song(externalId = WMA), playId = opening)).parameters["session"] }
                launch { ios.endPlay(ending) }
                opened.await()
            }
            val stopped = server.requestsTo(TRANSCODE_STOP).map { it.url.parameters["session"] }
            (session in stopped) shouldBe false
            ending = opening
        }
    }

    @Test
    fun `ending a play that opened no transcode asks nothing of the server`() = runTest {
        ios.streamUrl(song(externalId = PART), playId = "play-1")

        ios.endPlay("play-1")
        ios.endPlay("never-opened")

        server.requests.shouldBeEmpty()
    }

    @Test
    fun `a stop the server fails is not thrown`() = runTest {
        server.respond(TRANSCODE_STOP, code = 500)
        ios.streamUrl(song(externalId = WMA), playId = "play-1")

        ios.endPlay("play-1")

        server.requestsTo(TRANSCODE_STOP).size shouldBe 1
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
        const val WMA = "/library/parts/43/1600000000/file.wma"
        const val TRANSCODE_STOP = "/video/:/transcode/universal/stop"
    }
}
