package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.bodyText
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.TRANSCODE_DECISION
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.Url
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** Which stream a song plays from under the bitrate cap: the original, the server's transcode decision, or the classic transcode. */
class SubsonicStreamsTest {
    private val subsonic = TestSubsonic()
    private val server = subsonic.server

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private val bitrateCap = StreamingBitrateCap(streamingSettings) { false }

    private val streams = SubsonicStreams(subsonic.authenticationManager, subsonic.service, bitrateCap, StreamProfile.Android, "Shuttle")

    private val flac = subsonicSong()
    private val mp3 = subsonicSong(externalId = "0ZVb9IcFZ0wbcBgwwir582", mimeType = "audio/mpeg", bitRate = 192, audioCodec = "mp3")
    private val alac = subsonicSong(externalId = "alac-1", mimeType = "audio/mp4", bitRate = 900, audioCodec = "alac")

    @AfterTest
    fun tearDown() = subsonic.close()

    @Test
    fun `with no cap a decodable song streams its original file`() {
        subsonic.signIn()

        val stream = runBlocking { streams.stream(flac) }

        val url = Url(stream.url)
        url.encodedPath shouldBe "/rest/stream.view"
        url.parameters["id"] shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
        url.parameters["format"] shouldBe "raw"
        url.parameters["t"] shouldBe md5Hex(TestSubsonic.PASSWORD + url.parameters["s"])
        stream.mimeType shouldBe "audio/flac"
        stream.timeSeek.shouldBeNull()
        server.requestsTo(TRANSCODE_DECISION).shouldBeEmpty()
    }

    @Test
    fun `each stream url carries a fresh salt`() {
        subsonic.signIn()

        val first = Url(runBlocking { streams.stream(flac) }.url)
        val second = Url(runBlocking { streams.stream(flac) }.url)

        first.parameters["s"] shouldNotBe second.parameters["s"]
    }

    @Test
    fun `a song within the cap streams its original file`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps320

        Url(runBlocking { streams.stream(mp3) }.url).parameters["format"] shouldBe "raw"
    }

    @Test
    fun `past the cap - a server with transcoding streams what it decides`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128
        server.respond(TRANSCODE_DECISION, "transcode_decision.json", method = "POST")

        val stream = runBlocking { streams.stream(flac) }

        val decision = server.requestsTo(TRANSCODE_DECISION).single()
        decision.parameter("mediaId") shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
        decision.parameter("mediaType") shouldBe "song"
        decision.bodyText shouldContain "\"maxAudioBitrate\":128000"
        decision.bodyText shouldContain "\"container\":\"mp3\""
        val url = Url(stream.url)
        url.encodedPath shouldBe "/rest/getTranscodeStream.view"
        url.parameters["transcodeParams"] shouldBe "fixture-transcode-params"
        url.parameters["offset"].shouldBeNull()
        stream.mimeType shouldBe "audio/mpeg"
        val timeSeek = stream.timeSeek.shouldNotBeNull()
        timeSeek.bitrateKbps shouldBe 128
        timeSeek.durationMs shouldBe 142_000
        Url(timeSeek.urlAt(30)).parameters["offset"] shouldBe "30"
    }

    @Test
    fun `past the cap - a server without transcoding streams the classic MP3 transcode`() {
        subsonic.signIn(extensions = "ok.json")
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        val stream = runBlocking { streams.stream(flac) }

        val url = Url(stream.url)
        url.encodedPath shouldBe "/rest/stream.view"
        url.parameters["format"] shouldBe "mp3"
        url.parameters["maxBitRate"] shouldBe "128"
        url.parameters["estimateContentLength"] shouldBe "true"
        url.parameters["timeOffset"].shouldBeNull()
        Url(stream.timeSeek!!.urlAt(42)).parameters["timeOffset"] shouldBe "42"
        server.requestsTo(TRANSCODE_DECISION).shouldBeEmpty()
    }

    @Test
    fun `a failed transcode decision falls back to the classic transcode`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128
        server.respond(TRANSCODE_DECISION, "error_not_found.json", method = "POST")

        val url = Url(runBlocking { streams.stream(flac) }.url)

        url.parameters["format"] shouldBe "mp3"
        url.parameters["maxBitRate"] shouldBe "128"
    }

    @Test
    fun `a song the player can't decode is transcoded even with no cap`() {
        subsonic.signIn(extensions = "ok.json")

        val stream = runBlocking { streams.stream(alac) }

        Url(stream.url).parameters["maxBitRate"] shouldBe "320"
        stream.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `Cast gets the classic transcode - which it can seek itself`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        val stream = streams.castStream(flac)

        Url(stream.url).parameters["format"] shouldBe "mp3"
        stream.timeSeek.shouldBeNull()
    }

    @Test
    fun `a decodable song downloads its original file - ignoring the cap`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        val download = streams.downloadSource(flac).shouldNotBeNull()

        Url(download.url).encodedPath shouldBe "/rest/download.view"
        Url(download.url).parameters["id"] shouldBe "5zTXFMk8oDiQF9kh1gqcJE"
        download.mimeType shouldBe "audio/flac"
    }

    @Test
    fun `a song the player can't decode downloads as an MP3`() {
        subsonic.signIn()

        val download = streams.downloadSource(alac).shouldNotBeNull()

        Url(download.url).parameters["format"] shouldBe "mp3"
        download.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `nothing streams before a sign-in`() {
        runCatching { runBlocking { streams.stream(flac) } }.isFailure shouldBe true
        streams.downloadSource(flac).shouldBeNull()
    }

    @Test
    fun `the player and downloads stream through the url provider without asking the server`() {
        subsonic.signIn()
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128
        val provider = SubsonicStreamUrlProvider(streams)

        val url = Url(provider.streamUrl(flac, startPositionMs = 61_500, playId = null))

        provider.handles("subsonic") shouldBe true
        provider.handles("plex") shouldBe false
        url.parameters["format"] shouldBe "mp3"
        url.parameters["timeOffset"] shouldBe "61"
        server.requestsTo(TRANSCODE_DECISION).shouldBeEmpty()
    }
}
