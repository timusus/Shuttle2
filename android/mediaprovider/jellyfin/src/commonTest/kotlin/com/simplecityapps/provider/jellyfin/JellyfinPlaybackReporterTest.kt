package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.bodyText
import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.jellyfin.http.PlaybackReport
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

class JellyfinPlaybackReporterTest {
    private val server = FixtureServer("jellyfin")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
        streamProfile = StreamProfile.Android
    )

    private val reporter = JellyfinPlaybackReporter(authenticationManager, PlaybackReportingService(client))

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 180_000, date = null, genres = emptyList(), path = "jellyfin://item/item789", size = 0, mimeType = "audio/flac",
        lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
        externalId = "item789", mediaProvider = MediaProviderType.Jellyfin, lyrics = null, grouping = null, bitRate = null,
        bitDepth = null, sampleRate = null, channelCount = null
    )
    private val session = PlaybackSession(song, "session-1")

    init {
        for (path in listOf("/Sessions/Playing", "/Sessions/Playing/Progress", "/Sessions/Playing/Stopped", "/UserPlayedItems/item789")) {
            server.respond(path, code = 204, method = "POST")
        }
    }

    @Test
    fun `handles only Jellyfin songs`() {
        reporter.handles(song) shouldBe true
        reporter.handles(song.copy(mediaProvider = MediaProviderType.Emby)) shouldBe false
    }

    @Test
    fun `start posts the play to Sessions Playing`() {
        runBlocking { reporter.start(session, positionMs = 1_500) } shouldBe true

        val request = server.requestsTo("/Sessions/Playing").single()
        request.headers[HttpHeaders.Authorization]!!.contains("Token=\"token123\"") shouldBe true
        request.bodyText shouldBe
            """{"ItemId":"item789","PlaySessionId":"session-1","PositionTicks":15000000,"IsPaused":false,"CanSeek":true,"PlayMethod":"DirectStream"}"""
    }

    @Test
    fun `progress posts the position and pause state`() {
        runBlocking { reporter.progress(session, positionMs = 60_000, paused = true) } shouldBe true

        S2Json.decodeFromString<PlaybackReport>(server.requestsTo("/Sessions/Playing/Progress").single().bodyText) shouldBe
            PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 600_000_000, isPaused = true)
    }

    @Test
    fun `stop always carries the position`() {
        runBlocking { reporter.stop(session, positionMs = 0) } shouldBe true

        server.requestsTo("/Sessions/Playing/Stopped").single().bodyText.contains("\"PositionTicks\":0") shouldBe true
    }

    @Test
    fun `markPlayed backdates the play`() {
        runBlocking { reporter.markPlayed(song, Instant.parse("2026-09-24T10:15:30Z")) } shouldBe true

        val request = server.requestsTo("/UserPlayedItems/item789").single()
        request.url.parameters["userId"] shouldBe "user456"
        request.url.parameters["datePlayed"] shouldBe "2026-09-24T10:15:30Z"
    }

    @Test
    fun `a rejected call reports failure`() {
        server.respond("/Sessions/Playing", code = 401, method = "POST")

        runBlocking { reporter.start(session, positionMs = 0) } shouldBe false
    }

    @Test
    fun `nothing is sent when signed out`() {
        credentialStore.authenticatedCredentials = null

        runBlocking { reporter.start(session, positionMs = 0) } shouldBe false
        server.requests shouldBe emptyList()
    }
}
