package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.provider.emby.http.UserService
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
 * Exercises [EmbyMediaInfoProvider.downloadFallbackUri]'s 401-vs-403 handling (#322): a 403 means
 * the server actually revoked download permission, so it's persisted; a 401 can also mean the
 * cached session expired, so it isn't. Asserts on [EmbyMediaInfoProvider.buildFallbackPathString]
 * rather than [EmbyMediaInfoProvider.downloadFallbackUri] directly, since the latter calls
 * `String.toUri()`, which needs a mocked `android.net.Uri` and would pull Robolectric into this
 * module for nothing else. The stream URL and its bitrate cap (#504) are
 * [EmbyStreamUrlProviderTest]'s, in commonTest.
 */
class EmbyMediaInfoProviderTest {
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
        address = "http://emby.local:8096"
    }

    private val client = createHttpClient(FixtureServer("emby").engine)

    private val authenticationManager = EmbyAuthenticationManager(
        userService = UserService(client),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "1.0", deviceName = "TestDevice"),
        streamProfile = StreamProfile.Android
    )

    private val streamingSettings = StreamingSettings(SettingsStore(InMemoryKeyValueStore()))
    private var metered = false

    private val provider = EmbyMediaInfoProvider(
        authenticationManager,
        EmbyTranscodeService(client),
        StreamingPolicy(streamingSettings) { metered }
    )

    @Test
    fun `a 403 persists that download permission is disabled`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 403) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }

    @Test
    fun `a 401 leaves the stored download permission alone`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 401) }

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `fallback path is the static stream url`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        val path = runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 403) }!!

        path shouldBe "http://emby.local:8096/emby/Audio/item789/stream?static=true&api_key=token123"
    }

    @Test
    fun `the cap never reaches the download or static stream urls`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        streamingSettings.unmeteredQuality.value = StreamingQuality.Kbps128

        authenticationManager.buildDownloadPath("item789", downloadableCredentials)!! shouldNotContain "MaxStreamingBitrate"
        runBlocking { provider.buildFallbackPathString("emby://item/item789", responseCode = 401) }!! shouldNotContain "MaxStreamingBitrate"
    }
}
