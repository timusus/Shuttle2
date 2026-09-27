package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.Url
import org.junit.Test

/** Plex has no separate download endpoint: the part-file path is already the original file. */
class PlexAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(createHttpClient(FixtureServer { error("not called") }.engine)),
        credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
            address = "http://plex.local:32400"
        },
        clientIdentity = clientIdentity
    )

    @Test
    fun `part file url carries the plex token and client identity`() {
        val path = authenticationManager.buildPlexPath(song = song(externalId = "/library/parts/42/file.mp3"), authenticatedCredentials = credentials)!!

        path shouldBe "http://plex.local:32400/library/parts/42/file.mp3" +
            "?X-Plex-Token=token123" +
            "&X-Plex-Client-Identifier=${clientIdentity.id}" +
            "&X-Plex-Device=Android"
    }

    @Test
    fun `transcode url asks the universal transcoder for AAC over HLS at the cap`() {
        val path = authenticationManager.buildPlexTranscodePath(
            song = song(externalId = "/library/parts/42/file.flac", path = "plex:///library/metadata/107898"),
            authenticatedCredentials = credentials,
            maxBitrateKbps = 192,
            session = "session-1"
        )!!.let(::Url)

        path.encodedPath shouldBe "/music/:/transcode/universal/start.m3u8"
        path.parameters["path"] shouldBe "/library/metadata/107898"
        path.parameters["protocol"] shouldBe "hls"
        path.parameters["directPlay"] shouldBe "0"
        path.parameters["directStream"] shouldBe "0"
        path.parameters["musicBitrate"] shouldBe "192"
        path.parameters["session"] shouldBe "session-1"
        path.parameters["X-Plex-Client-Profile-Extra"] shouldBe
            "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)"
        path.parameters["X-Plex-Token"] shouldBe "token123"
        path.parameters["X-Plex-Client-Identifier"] shouldBe clientIdentity.id
    }

    @Test
    fun `transcode url is null for a song with no ratingKey`() {
        authenticationManager.buildPlexTranscodePath(
            song = song(externalId = "/library/parts/42/file.flac", path = "plex:///library/parts/42/file.flac"),
            authenticatedCredentials = credentials,
            maxBitrateKbps = 192
        ) shouldBe null
    }

    @Test
    fun `progressive transcode url asks the universal transcoder for a single MP3 file, not HLS (#567)`() {
        val path = authenticationManager.buildPlexProgressiveTranscodePath(
            song = song(externalId = "/library/parts/42/file.wma", path = "plex:///library/metadata/107898"),
            authenticatedCredentials = credentials,
            bitrateKbps = 320
        )!!.let(::Url)

        path.encodedPath shouldBe "/music/:/transcode/universal/start.mp3"
        path.parameters["path"] shouldBe "/library/metadata/107898"
        path.parameters["protocol"] shouldBe "http"
        path.parameters["directPlay"] shouldBe "0"
        path.parameters["directStream"] shouldBe "0"
        path.parameters["musicBitrate"] shouldBe "320"
        path.parameters["X-Plex-Client-Profile-Extra"] shouldBe
            "add-transcode-target(type=musicProfile&context=static&protocol=http&container=mp3&audioCodec=mp3)"
        path.parameters["X-Plex-Token"] shouldBe "token123"
        path.parameters["X-Plex-Client-Identifier"] shouldBe clientIdentity.id
    }

    @Test
    fun `progressive transcode url is null for a song with no ratingKey`() {
        authenticationManager.buildPlexProgressiveTranscodePath(
            song = song(externalId = "/library/parts/42/file.wma", path = "plex:///library/parts/42/file.wma"),
            authenticatedCredentials = credentials,
            bitrateKbps = 320
        ) shouldBe null
    }

    private fun song(
        externalId: String?,
        path: String = "plex://item/107898"
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
        path = path,
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
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
