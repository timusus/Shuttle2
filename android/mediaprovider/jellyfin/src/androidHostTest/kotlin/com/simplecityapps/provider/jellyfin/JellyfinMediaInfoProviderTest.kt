package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Exercises [JellyfinMediaInfoProvider.downloadFallbackUri]'s 401-vs-403 handling (#322): a 403
 * means the server actually revoked download permission, so it's persisted; a 401 can also mean
 * the cached session expired, so it isn't. Asserts on [JellyfinMediaInfoProvider.buildFallbackPathString]
 * rather than [JellyfinMediaInfoProvider.downloadFallbackUri] directly, since the latter calls
 * `String.toUri()`, which needs a mocked `android.net.Uri` and would pull Robolectric into this
 * module for nothing else. The stream URL and its bitrate cap (#504) are
 * [JellyfinStreamUrlProviderTest]'s, in commonTest.
 */
class JellyfinMediaInfoProviderTest {
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin").apply {
        address = "http://jellyfin.local:8096"
    }

    private val client = createHttpClient(FixtureServer("jellyfin").engine)

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
        streamProfile = StreamProfile.Android
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false

    private val provider = JellyfinMediaInfoProvider(
        authenticationManager,
        JellyfinTranscodeService(client),
        StreamingBitrateCap(streamingSettings) { metered }
    )

    @Test
    fun `a 403 persists that download permission is disabled`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("jellyfin://item/item789", responseCode = 403) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }

    @Test
    fun `a 401 leaves the stored download permission alone`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("jellyfin://item/item789", responseCode = 401) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `fallback path is the static stream url`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = runBlocking { provider.buildFallbackPathString("jellyfin://item/item789", responseCode = 403) }!!

        path shouldBe "http://jellyfin.local:8096/Audio/item789/stream?static=true&ApiKey=token123"
    }

    @Test
    fun `the cap never reaches the download or static stream urls`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        authenticationManager.buildDownloadPath("item789", downloadableCredentials)!! shouldNotContain "MaxStreamingBitrate"
        runBlocking { provider.buildFallbackPathString("jellyfin://item/item789", responseCode = 401) }!! shouldNotContain "MaxStreamingBitrate"
    }
}
