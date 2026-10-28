package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.bodyText
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.streaming.DeliveredFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/** Whether a stream is a transcode is the server's PlaybackInfo decision, not the client's guess from the song's bitrate. */
class MediaBrowserStreamUrlProviderTest {
    private class Rig(val mediaBrowserServer: MediaBrowserServer) {
        val server = FixtureServer { name -> FIXTURES.getValue(name) }
        val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
        val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), mediaBrowserServer.name.lowercase()).apply {
            address = server.address
            authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
        }
        val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore())).apply {
            unmeteredQuality.value = StreamingQuality.Kbps320
        }
        val deliveredFormats = DeliveredFormats()
        val provider = MediaBrowserStreamUrlProvider(
            MediaBrowserAuthenticationManager(
                mediaBrowserServer,
                UserService(createHttpClient(server.engine), mediaBrowserServer, clientIdentity),
                credentialStore,
                clientIdentity,
                StreamProfile.Ios
            ),
            StreamingPolicy(streamingSettings, deliveredFormats) { false }
        )
        val path = "${mediaBrowserServer.apiPrefix}/Items/item789/PlaybackInfo"

        // Over the 320 kbps cap, so the client alone would guess a transcode.
        val song = Song(
            id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
            duration = 180_000, date = null, genres = emptyList(), path = "${mediaBrowserServer.songPathPrefix}item789", size = 0, mimeType = "audio/flac",
            lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
            externalId = "item789", mediaProvider = mediaBrowserServer.type, lyrics = null, grouping = null, bitRate = 900,
            bitDepth = null, sampleRate = null, channelCount = null, audioCodec = "flac"
        )
    }

    // The mock engine answers off the test scheduler, so virtual time would fire the provider's timeout before any reply:
    // real time here, virtual only for the stall.
    private fun eachServer(block: suspend Rig.() -> Unit) = runTest {
        withContext(Dispatchers.Default) { MediaBrowserServer.entries.forEach { Rig(it).block() } }
    }

    @Test
    fun `a song over the cap the server direct-plays is opened as the original file`() = eachServer {
        server.respond(path, fixture = "direct", method = "POST")

        val stream = provider.openStream(song, startPositionMs = 205_000)

        stream.transcoded shouldBe false
        deliveredFormats.byPath.value[song.path] shouldBe null
    }

    @Test
    fun `a song the server transcodes is opened as a transcode`() = eachServer {
        server.respond(path, fixture = "transcode", method = "POST")

        provider.openStream(song).transcoded shouldBe true
        deliveredFormats.byPath.value[song.path] shouldBe DeliveredFormat("MP3", 320)
    }

    @Test
    fun `the server is asked with the universal stream's cap and profile`() = eachServer {
        server.respond(path, fixture = "direct", method = "POST")

        provider.openStream(song)

        val request = server.requestsTo(path).single()
        request.url.parameters["UserId"] shouldBe "user456"
        request.url.parameters["MaxStreamingBitrate"] shouldBe "320000"
        request.bodyText shouldContain """{"Type":"Audio","Container":"flac"}"""
        request.bodyText shouldContain """{"Type":"Audio","Container":"mp3","AudioCodec":"mp3","Protocol":"http","Context":"Streaming"}"""
    }

    @Test
    fun `a server that can't answer leaves the client's guess`() = eachServer {
        server.respond(path, code = 500, method = "POST")

        provider.openStream(song).transcoded shouldBe true
    }

    @Test
    fun `a file the server direct-plays but won't direct-stream is a transcode`() = eachServer {
        server.respond(path, fixture = "directPlayOnly", method = "POST")

        provider.openStream(song).transcoded shouldBe true
    }

    @Test
    fun `a file the server direct-streams but won't direct-play is the original`() = eachServer {
        server.respond(path, fixture = "directStreamOnly", method = "POST")

        provider.openStream(song).transcoded shouldBe false
    }

    @Test
    fun `a server that names no source leaves the client's guess`() = eachServer {
        server.respond(path, fixture = "noSources", method = "POST")

        provider.openStream(song).transcoded shouldBe true
    }

    @Test
    fun `a server that stalls leaves the client's guess`() = runTest {
        MediaBrowserServer.entries.forEach { mediaBrowserServer ->
            with(Rig(mediaBrowserServer)) {
                server.respond(path, fixture = "direct", method = "POST", delayMs = 60_000)

                provider.openStream(song).transcoded shouldBe true
            }
        }
    }

    @Test
    fun `a song reopened on a seek keeps the server's answer without asking again`() = eachServer {
        server.respond(path, fixture = "direct", method = "POST")
        provider.openStream(song).transcoded shouldBe false

        server.respond(path, code = 500, method = "POST")
        provider.openStream(song, startPositionMs = 205_000).transcoded shouldBe false

        server.requestsTo(path).size shouldBe 1
    }

    private companion object {
        val FIXTURES = mapOf(
            "noSources" to """{"MediaSources":[]}""",
            "directPlayOnly" to """{"MediaSources":[{"SupportsDirectPlay":true,"SupportsDirectStream":false,"SupportsTranscoding":true}]}""",
            "directStreamOnly" to """{"MediaSources":[{"SupportsDirectPlay":false,"SupportsDirectStream":true,"SupportsTranscoding":true}]}""",
            "direct" to """{"MediaSources":[{"SupportsDirectPlay":true,"SupportsDirectStream":true,"SupportsTranscoding":true}]}""",
            "transcode" to """{"MediaSources":[{"SupportsDirectPlay":false,"SupportsDirectStream":false,"SupportsTranscoding":true,""" +
                """"TranscodingUrl":"/Audio/item789/universal?AudioCodec=mp3"}]}"""
        )
    }
}
