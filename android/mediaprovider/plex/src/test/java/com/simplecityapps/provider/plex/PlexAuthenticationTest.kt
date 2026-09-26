package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/** Plex has no separate download endpoint: the part-file path is already the original file. */
class PlexAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = PlexAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                login: String,
                password: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
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
        )!!.toHttpUrl()

        path.encodedPath shouldBe "/music/:/transcode/universal/start.m3u8"
        path.queryParameter("path") shouldBe "/library/metadata/107898"
        path.queryParameter("protocol") shouldBe "hls"
        path.queryParameter("directPlay") shouldBe "0"
        path.queryParameter("directStream") shouldBe "0"
        path.queryParameter("musicBitrate") shouldBe "192"
        path.queryParameter("session") shouldBe "session-1"
        path.queryParameter("X-Plex-Client-Profile-Extra") shouldBe
            "add-transcode-target(type=musicProfile&context=streaming&protocol=hls&container=mpegts&audioCodec=aac)"
        path.queryParameter("X-Plex-Token") shouldBe "token123"
        path.queryParameter("X-Plex-Client-Identifier") shouldBe clientIdentity.id
    }

    @Test
    fun `transcode url is null for a song with no ratingKey`() {
        authenticationManager.buildPlexTranscodePath(
            song = song(externalId = "/library/parts/42/file.flac", path = "plex:///library/parts/42/file.flac"),
            authenticatedCredentials = credentials,
            maxBitrateKbps = 192
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
