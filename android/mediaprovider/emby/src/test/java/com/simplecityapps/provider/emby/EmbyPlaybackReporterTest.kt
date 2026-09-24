package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.PlaybackReport
import com.simplecityapps.provider.emby.http.PlaybackReportingService
import com.simplecityapps.provider.emby.http.User
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Test
import retrofit2.Response

class EmbyPlaybackReporterTest {
    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://emby.local:8096"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val authenticationManager = EmbyAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                body: Map<String, String>,
                header: String
            ): NetworkResult<AuthenticationResult> = error("not called")

            override suspend fun meImpl(
                url: String,
                token: String
            ): NetworkResult<User> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
    )

    private val service = FakeService()
    private val reporter = EmbyPlaybackReporter(authenticationManager, service)

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

        service.url shouldBe "http://emby.local:8096/emby/Sessions/Playing"
        service.token shouldBe "token123"
        service.authorization!!.startsWith("MediaBrowser ") shouldBe true
        service.authorization!!.contains("DeviceId=\"device-1\"") shouldBe true
        Moshi.Builder().build().adapter(PlaybackReport::class.java).toJson(service.report) shouldBe
            """{"ItemId":"item789","PlaySessionId":"session-1","PositionTicks":15000000,"IsPaused":false,"CanSeek":true,"PlayMethod":"DirectStream"}"""
    }

    @Test
    fun `progress and stop post to their endpoints`() {
        runBlocking { reporter.progress(session, positionMs = 60_000, paused = true) }
        service.url shouldBe "http://emby.local:8096/emby/Sessions/Playing/Progress"
        service.report shouldBe PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 600_000_000, isPaused = true)

        runBlocking { reporter.stop(session, positionMs = 180_000) }
        service.url shouldBe "http://emby.local:8096/emby/Sessions/Playing/Stopped"
        service.report shouldBe PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 1_800_000_000, isPaused = false)
    }

    @Test
    fun `markPlayed posts to the user's played items with the play date`() {
        runBlocking { reporter.markPlayed(song, Instant.parse("2026-09-04T08:05:03Z")) } shouldBe true

        service.url shouldBe "http://emby.local:8096/emby/Users/user456/PlayedItems/item789"
        service.datePlayed shouldBe "20260904080503"
    }

    @Test
    fun `the play date is formatted in UTC`() {
        embyDatePlayed(Instant.parse("2026-12-31T23:59:59.999Z")) shouldBe "20261231235959"
    }

    private class FakeService : PlaybackReportingService {
        var url: String? = null
        var token: String? = null
        var authorization: String? = null
        var report: PlaybackReport? = null
        var datePlayed: String? = null

        override suspend fun report(
            url: String,
            token: String,
            authorization: String,
            report: PlaybackReport
        ): Response<Unit> {
            this.url = url
            this.token = token
            this.authorization = authorization
            this.report = report
            return Response.success(Unit)
        }

        override suspend fun markPlayed(
            url: String,
            token: String,
            authorization: String,
            datePlayed: String
        ): Response<Unit> {
            this.url = url
            this.token = token
            this.authorization = authorization
            this.datePlayed = datePlayed
            return Response.success(Unit)
        }
    }
}
