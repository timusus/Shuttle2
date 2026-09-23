package com.simplecityapps.provider.jellyfin

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.AuthenticatedCredentials
import com.simplecityapps.provider.jellyfin.http.AuthenticationResult
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.provider.jellyfin.http.mediaBrowserAuthorization
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test

/** Jellyfin 12 only accepts the `MediaBrowser ... Token=` header and the `ApiKey=` query param (#308). */
class JellyfinAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                body: Map<String, String>,
                header: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
            address = "http://jellyfin.local:8096"
        }
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
}
