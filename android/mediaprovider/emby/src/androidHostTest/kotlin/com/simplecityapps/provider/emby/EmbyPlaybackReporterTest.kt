package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.bodyText
import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.http.PlaybackReport
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EmbyPlaybackReporterTest {
    private val server = FixtureServer("emby")
    private val client = createHttpClient(server.engine)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
        address = server.address
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val authenticationManager = EmbyAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
    )

    private val reporter = EmbyPlaybackReporter(authenticationManager, PlaybackReportingService(client))

    init {
        for (path in listOf("/emby/Sessions/Playing", "/emby/Sessions/Playing/Progress", "/emby/Sessions/Playing/Stopped", "/emby/Users/user456/PlayedItems/item789")) {
            server.respond(path, code = 204, method = "POST")
        }
    }

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 180_000, date = null, genres = emptyList(), path = "emby://item/item789", size = 0, mimeType = "audio/flac",
        lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
        externalId = "item789", mediaProvider = MediaProviderType.Emby, lyrics = null, grouping = null, bitRate = null,
        bitDepth = null, sampleRate = null, channelCount = null
    )
    private val session = PlaybackSession(song, "session-1")

    @Test
    fun `handles only Emby songs`() {
        reporter.handles(song) shouldBe true
        reporter.handles(song.copy(mediaProvider = MediaProviderType.Jellyfin)) shouldBe false
    }

    @Test
    fun `start posts the play under the emby path with the token and client headers`() {
        runBlocking { reporter.start(session, positionMs = 1_500) } shouldBe true

        val request = server.requestsTo("/emby/Sessions/Playing").single()
        request.headers["X-Emby-Token"] shouldBe "token123"
        request.headers["X-Emby-Authorization"]!!.startsWith("MediaBrowser ") shouldBe true
        request.headers["X-Emby-Authorization"]!!.contains("DeviceId=\"device-1\"") shouldBe true
        request.bodyText shouldBe
            """{"ItemId":"item789","PlaySessionId":"session-1","PositionTicks":15000000,"IsPaused":false,"CanSeek":true,"PlayMethod":"DirectStream"}"""
    }

    @Test
    fun `progress and stop post to their endpoints`() {
        runBlocking { reporter.progress(session, positionMs = 60_000, paused = true) } shouldBe true
        S2Json.decodeFromString<PlaybackReport>(server.requestsTo("/emby/Sessions/Playing/Progress").single().bodyText) shouldBe
            PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 600_000_000, isPaused = true)

        runBlocking { reporter.stop(session, positionMs = 180_000) } shouldBe true
        S2Json.decodeFromString<PlaybackReport>(server.requestsTo("/emby/Sessions/Playing/Stopped").single().bodyText) shouldBe
            PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 1_800_000_000, isPaused = false)
    }

    @Test
    fun `markPlayed posts to the user's played items with the play date`() {
        runBlocking { reporter.markPlayed(song, Instant.parse("2026-09-04T08:05:03Z")) } shouldBe true

        server.requestsTo("/emby/Users/user456/PlayedItems/item789").single().url.parameters["DatePlayed"] shouldBe "20260904080503"
    }

    @Test
    fun `a rejected call reports failure and signs out`() {
        server.respond("/emby/Sessions/Playing", code = 401, method = "POST")

        runBlocking { reporter.start(session, positionMs = 0) } shouldBe false
        credentialStore.authenticatedCredentials shouldBe null
    }

    @Test
    fun `the play date is formatted in UTC`() {
        embyDatePlayed(Instant.parse("2026-12-31T23:59:59.999Z")) shouldBe "20261231235959"
    }
}
