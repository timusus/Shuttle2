package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.PlaybackReportingService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.HttpRequestData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class PlexPlaybackReporterTest {
    private val server = FixtureServer("plex")

    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    }

    private val authenticationManager = PlexAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")
    )

    private val reporter = PlexPlaybackReporter(authenticationManager, PlaybackReportingService(client))

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 200_000, date = null, genres = emptyList(), path = "plex:///library/metadata/107898", size = 0,
        mimeType = "audio/mpeg", lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0,
        playbackPosition = 0, blacklisted = false, externalId = "/library/parts/42/file.mp3", mediaProvider = MediaProviderType.Plex,
        lyrics = null, grouping = null, bitRate = null, bitDepth = null, sampleRate = null, channelCount = null
    )
    private val session = PlaybackSession(song, "session-1")

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `handles only Plex songs`() {
        reporter.handles(song) shouldBe true
        reporter.handles(song.copy(mediaProvider = MediaProviderType.Jellyfin)) shouldBe false
    }

    @Test
    fun `start and progress report the timeline state`() = runTest {
        server.respond(TIMELINE)

        reporter.start(session, positionMs = 1_500) shouldBe true
        reporter.progress(session, positionMs = 30_000, paused = true)
        reporter.progress(session, positionMs = 30_000, paused = false)

        server.requestsTo(TIMELINE).map { it.reported() } shouldBe listOf(timeline("playing", 1_500), timeline("paused", 30_000), timeline("playing", 30_000))
    }

    @Test
    fun `the timeline carries the session token`() = runTest {
        server.respond(TIMELINE)

        reporter.start(session, positionMs = 0)

        server.requestsTo(TIMELINE).single().headers["X-Plex-Token"] shouldBe "token123"
    }

    @Test
    fun `stopping reports the stopped timeline without a scrobble - even at the end`() = runTest {
        server.respond(TIMELINE)

        reporter.stop(session, positionMs = 200_000) shouldBe true

        server.requests.map { it.reported() } shouldBe listOf(timeline("stopped", 200_000))
    }

    @Test
    fun `markPlayed scrobbles`() = runTest {
        server.respond(SCROBBLE)

        reporter.markPlayed(song, Instant.parse("2026-09-24T10:00:00Z")) shouldBe true

        server.requests.map { it.reported() } shouldBe listOf(Reported(SCROBBLE, mapOf("key" to "107898", "identifier" to "com.plexapp.plugins.library")))
    }

    @Test
    fun `a failed report reports failure and keeps the session`() = runTest {
        server.respond(TIMELINE, code = 500)

        reporter.start(session, positionMs = 0) shouldBe false
        credentialStore.authenticatedCredentials.shouldNotBeNull()
    }

    @Test
    fun `a rejected report reports failure and signs out`() = runTest {
        server.respond(TIMELINE, code = 401)

        reporter.start(session, positionMs = 0) shouldBe false
        credentialStore.authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `a song without a metadata path is not reported`() = runTest {
        reporter.start(PlaybackSession(song.copy(path = "plex://item/107898"), "session-1"), positionMs = 0) shouldBe false

        server.requests.shouldBeEmpty()
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

    private data class Reported(
        val path: String,
        val params: Map<String, String>
    )

    private fun HttpRequestData.reported() = Reported(url.encodedPath, url.parameters.names().associateWith { name -> url.parameters[name]!! })

    private fun timeline(
        state: String,
        timeMs: Int
    ) = Reported(
        TIMELINE,
        mapOf(
            "ratingKey" to "107898",
            "key" to "/library/metadata/107898",
            "identifier" to "com.plexapp.plugins.library",
            "state" to state,
            "time" to timeMs.toString(),
            "duration" to "200000"
        )
    )

    private companion object {
        const val TIMELINE = "/:/timeline"
        const val SCROBBLE = "/:/scrobble"
    }
}
