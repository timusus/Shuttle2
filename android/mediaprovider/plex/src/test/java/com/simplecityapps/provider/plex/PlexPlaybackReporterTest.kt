package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.AuthenticatedCredentials
import com.simplecityapps.provider.plex.http.AuthenticationResult
import com.simplecityapps.provider.plex.http.PlaybackReportingService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test
import retrofit2.Response

class PlexPlaybackReporterTest {
    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://plex.local:32400"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    private val authenticationManager = PlexAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                login: String,
                password: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")
    )

    private val service = FakeService()
    private val reporter = PlexPlaybackReporter(authenticationManager, service)

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 200_000, date = null, genres = emptyList(), path = "plex:///library/metadata/107898", size = 0,
        mimeType = "audio/mpeg", lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0,
        playbackPosition = 0, blacklisted = false, externalId = "/library/parts/42/file.mp3", mediaProvider = MediaProviderType.Plex,
        lyrics = null, grouping = null, bitRate = null, bitDepth = null, sampleRate = null, channelCount = null
    )
    private val session = PlaybackSession(song, "session-1")

    private fun timeline(
        state: String,
        timeMs: Int
    ) = FakeService.Request(
        url = "http://plex.local:32400/:/timeline",
        token = "token123",
        params = mapOf(
            "ratingKey" to "107898",
            "key" to "/library/metadata/107898",
            "identifier" to "com.plexapp.plugins.library",
            "state" to state,
            "time" to timeMs.toString(),
            "duration" to "200000"
        )
    )

    private val scrobble = FakeService.Request(
        url = "http://plex.local:32400/:/scrobble",
        token = "token123",
        params = mapOf("key" to "107898", "identifier" to "com.plexapp.plugins.library")
    )

    @Test
    fun `handles only Plex songs`() {
        reporter.handles(song) shouldBe true
        reporter.handles(song.copy(mediaProvider = MediaProviderType.Jellyfin)) shouldBe false
    }

    @Test
    fun `start and progress report the timeline state`() = runTest {
        reporter.start(session, positionMs = 1_500) shouldBe true
        reporter.progress(session, positionMs = 30_000, paused = true)
        reporter.progress(session, positionMs = 30_000, paused = false)

        service.requests shouldBe listOf(timeline("playing", 1_500), timeline("paused", 30_000), timeline("playing", 30_000))
    }

    @Test
    fun `stopping reports the stopped timeline without a scrobble, even at the end`() = runTest {
        reporter.stop(session, positionMs = 200_000) shouldBe true

        service.requests shouldBe listOf(timeline("stopped", 200_000))
    }

    @Test
    fun `markPlayed scrobbles`() = runTest {
        reporter.markPlayed(song, Instant.parse("2026-09-24T10:00:00Z")) shouldBe true

        service.requests shouldBe listOf(scrobble)
    }

    @Test
    fun `a song without a metadata path is not reported`() = runTest {
        reporter.start(PlaybackSession(song.copy(path = "plex://item/107898"), "session-1"), positionMs = 0) shouldBe false

        service.requests shouldBe emptyList()
    }

    @Test
    fun `ratingKey is parsed from the metadata path`() {
        plexRatingKey("plex:///library/metadata/107898") shouldBe "107898"
        plexRatingKey("/library/metadata/107898") shouldBe "107898"
        plexRatingKey("plex:///library/metadata/107898/children") shouldBe "107898"
        plexRatingKey("plex:///library/metadata/") shouldBe null
        plexRatingKey("plex:///library/parts/42/file.mp3") shouldBe null
        plexRatingKey("plex://item/107898") shouldBe null
        plexRatingKey("") shouldBe null
    }

    private class FakeService : PlaybackReportingService {
        data class Request(
            val url: String,
            val token: String,
            val params: Map<String, String>
        )

        val requests = mutableListOf<Request>()

        override suspend fun timeline(
            url: String,
            token: String,
            ratingKey: String,
            key: String,
            identifier: String,
            state: String,
            timeMs: Int,
            durationMs: Int
        ): Response<Unit> {
            requests += Request(
                url,
                token,
                mapOf(
                    "ratingKey" to ratingKey,
                    "key" to key,
                    "identifier" to identifier,
                    "state" to state,
                    "time" to timeMs.toString(),
                    "duration" to durationMs.toString()
                )
            )
            return Response.success(Unit)
        }

        override suspend fun scrobble(
            url: String,
            token: String,
            ratingKey: String,
            identifier: String
        ): Response<Unit> {
            requests += Request(url, token, mapOf("key" to ratingKey, "identifier" to identifier))
            return Response.success(Unit)
        }
    }
}
