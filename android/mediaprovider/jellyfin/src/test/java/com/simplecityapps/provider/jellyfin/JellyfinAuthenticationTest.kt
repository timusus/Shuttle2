package com.simplecityapps.provider.jellyfin

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.Policy
import com.simplecityapps.provider.jellyfin.http.User
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.provider.jellyfin.http.mediaBrowserAuthorization
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Jellyfin 12 only accepts the `MediaBrowser ... Token=` header and the `ApiKey=` query param (#308). */
class JellyfinAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private lateinit var meResult: NetworkResult<User>

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
            ): NetworkResult<User> = meResult
        },
        credentialStore = credentialStore
    )

    @Test
    fun `authorization header carries the client identity and the token`() {
        mediaBrowserAuthorization(deviceId = "device-1", token = "token123", deviceName = "Pixel", version = "1.0") shouldBe
            "MediaBrowser Client=\"Shuttle2.0\", Device=\"Pixel\", DeviceId=\"device-1\", Version=\"1.0\", Token=\"token123\""
    }

    @Test
    fun `sign-in header has no token`() {
        mediaBrowserAuthorization(deviceId = "device-1", deviceName = "Pixel") shouldNotContain "Token="
    }

    @Test
    fun `authorization header for credentials uses the access token`() {
        authenticationManager.authorizationHeader(credentials) shouldContain "Token=\"token123\""
    }

    @Test
    fun `stream url authenticates with ApiKey, not api_key`() {
        val path = authenticationManager.buildJellyfinPath("item789", credentials)!!

        path shouldContain "&ApiKey=token123"
        path shouldNotContain "api_key"
        path shouldContain "http://jellyfin.local:8096/Audio/item789/universal?UserId=user456"
    }

    @Test
    fun `download url prefers the Download endpoint when the user can download`() {
        val path = authenticationManager.buildDownloadPath("item789", downloadableCredentials)!!

        path shouldBe "http://jellyfin.local:8096/Items/item789/Download?ApiKey=token123"
    }

    @Test
    fun `download url falls back to the static stream when the user can't download`() {
        val path = authenticationManager.buildDownloadPath("item789", credentials)!!

        path shouldBe "http://jellyfin.local:8096/Audio/item789/stream?static=true&ApiKey=token123"
    }

    @Test
    fun `refreshDownloadPermission picks up a permission the server granted after login`() {
        meResult = NetworkResult.Success(User(id = "user456", name = "User", policy = Policy(enableContentDownloading = true)))

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission("http://jellyfin.local:8096", credentials) }

        refreshed.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission keeps the cached permission when the request fails`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        meResult = NetworkResult.Failure(RuntimeException("network error"))

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission("http://jellyfin.local:8096", downloadableCredentials) }

        refreshed.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `disableDownloadPermission persists that the user can't use the Download endpoint`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        authenticationManager.disableDownloadPermission()

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }
}
