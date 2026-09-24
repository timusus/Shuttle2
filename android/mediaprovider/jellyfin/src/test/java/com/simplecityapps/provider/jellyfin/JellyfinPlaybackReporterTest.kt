package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.PlaybackReport
import com.simplecityapps.provider.jellyfin.http.PlaybackReportingService
import com.simplecityapps.provider.jellyfin.http.User
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class JellyfinPlaybackReporterTest {
    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://jellyfin.local:8096"
        authenticatedCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = false)
    }

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                body: Map<String, String>,
                header: String
            ): NetworkResult<AuthenticationResult> = error("not called")

            override suspend fun meImpl(
                url: String,
                authorization: String
            ): NetworkResult<User> = error("not called")
        },
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice")
    )

    private val service = FakeService()
    private val reporter = JellyfinPlaybackReporter(authenticationManager, service)

    private val song = Song(
        id = 1, name = "Song", albumArtist = null, artists = emptyList(), album = null, track = null, disc = null,
        duration = 180_000, date = null, genres = emptyList(), path = "jellyfin://item/item789", size = 0, mimeType = "audio/flac",
        lastModified = null, lastPlayed = null, lastCompleted = null, playCount = 0, playbackPosition = 0, blacklisted = false,
        externalId = "item789", mediaProvider = MediaProviderType.Jellyfin, lyrics = null, grouping = null, bitRate = null,
        bitDepth = null, sampleRate = null, channelCount = null
    )
    private val session = PlaybackSession(song, "session-1")

    private val reportAdapter = Moshi.Builder().build().adapter(PlaybackReport::class.java)

    @Test
    fun `handles only Jellyfin songs`() {
        reporter.handles(song) shouldBe true
        reporter.handles(song.copy(mediaProvider = MediaProviderType.Emby)) shouldBe false
    }

    @Test
    fun `start posts the play to Sessions Playing`() {
        runBlocking { reporter.start(session, positionMs = 1_500) } shouldBe true

        service.url shouldBe "http://jellyfin.local:8096/Sessions/Playing"
        service.authorization!!.contains("Token=\"token123\"") shouldBe true
        reportAdapter.toJson(service.report) shouldBe
            """{"ItemId":"item789","PlaySessionId":"session-1","PositionTicks":15000000,"IsPaused":false,"CanSeek":true,"PlayMethod":"DirectStream"}"""
    }

    @Test
    fun `progress posts the position and pause state`() {
        runBlocking { reporter.progress(session, positionMs = 60_000, paused = true) }

        service.url shouldBe "http://jellyfin.local:8096/Sessions/Playing/Progress"
        service.report shouldBe PlaybackReport(itemId = "item789", playSessionId = "session-1", positionTicks = 600_000_000, isPaused = true)
    }

    @Test
    fun `stop always carries the position`() {
        runBlocking { reporter.stop(session, positionMs = 0) }

        service.url shouldBe "http://jellyfin.local:8096/Sessions/Playing/Stopped"
        reportAdapter.toJson(service.report).contains("\"PositionTicks\":0") shouldBe true
    }

    @Test
    fun `markPlayed backdates the play`() {
        runBlocking { reporter.markPlayed(song, Instant.parse("2026-09-24T10:15:30Z")) } shouldBe true

        service.url shouldBe "http://jellyfin.local:8096/UserPlayedItems/item789"
        service.userId shouldBe "user456"
        service.datePlayed shouldBe "2026-09-24T10:15:30Z"
    }

    @Test
    fun `a rejected call reports failure`() {
        service.response = Response.error(401, "".toResponseBody())

        runBlocking { reporter.start(session, positionMs = 0) } shouldBe false
    }

    @Test
    fun `nothing is sent when signed out`() {
        credentialStore.authenticatedCredentials = null

        runBlocking { reporter.start(session, positionMs = 0) } shouldBe false
        service.url shouldBe null
    }

    private class FakeService : PlaybackReportingService {
        var response: Response<Unit> = Response.success(Unit)
        var url: String? = null
        var authorization: String? = null
        var report: PlaybackReport? = null
        var userId: String? = null
        var datePlayed: String? = null

        override suspend fun report(
            url: String,
            authorization: String,
            report: PlaybackReport
        ): Response<Unit> {
            this.url = url
            this.authorization = authorization
            this.report = report
            return response
        }

        override suspend fun markPlayed(
            url: String,
            authorization: String,
            userId: String,
            datePlayed: String
        ): Response<Unit> {
            this.url = url
            this.authorization = authorization
            this.userId = userId
            this.datePlayed = datePlayed
            return response
        }
    }
}
