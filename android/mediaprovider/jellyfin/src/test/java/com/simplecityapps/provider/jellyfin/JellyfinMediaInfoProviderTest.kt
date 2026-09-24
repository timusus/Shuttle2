package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.provider.jellyfin.http.User
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Exercises [JellyfinMediaInfoProvider.downloadFallbackUri]'s 401-vs-403 handling (#322): a 403
 * means the server actually revoked download permission, so it's persisted; a 401 can also mean
 * the cached session expired, so it isn't. Asserts on [JellyfinMediaInfoProvider.buildFallbackPathString]
 * rather than [JellyfinMediaInfoProvider.downloadFallbackUri] directly, since the latter calls
 * `String.toUri()`, which needs a mocked `android.net.Uri` and would pull Robolectric into this
 * module for nothing else.
 */
class JellyfinMediaInfoProviderTest {
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://jellyfin.local:8096"
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

    private val provider = JellyfinMediaInfoProvider(
        authenticationManager,
        object : JellyfinTranscodeService {
            override suspend fun transcode(url: String) = error("not called")
        }
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
}
