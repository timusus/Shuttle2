package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FakeSharedPreferences
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.mediaBrowserAuthorization
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.jellyfin.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Jellyfin 12 only accepts the `MediaBrowser ... Token=` header and the `ApiKey=` query param (#308). */
class JellyfinAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val server = FixtureServer("jellyfin")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(FakeSharedPreferences()), "jellyfin").apply {
        address = "http://jellyfin.local:8096"
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = UserService(createHttpClient(server.engine)),
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    @Test
    fun `authorization header carries the client identity and the token`() {
        mediaBrowserAuthorization(deviceId = "device-1", token = "token123", deviceName = "Pixel", version = "1.0") shouldBe
            "MediaBrowser Client=\"Shuttle2.0\", Device=\"Pixel\", DeviceId=\"device-1\", Version=\"1.0\", Token=\"token123\""
    }

    @Test
    fun `sign-in header has no token`() {
        mediaBrowserAuthorization(deviceId = "device-1", deviceName = "Pixel", version = "1.0") shouldNotContain "Token="
    }

    @Test
    fun `authorization header for credentials uses the access token`() {
        authenticationManager.authorizationHeader(credentials) shouldContain "Token=\"token123\""
    }

    @Test
    fun `authorization header carries the persisted client identity's device id`() {
        authenticationManager.authorizationHeader(credentials) shouldContain "DeviceId=\"${clientIdentity.id}\""
    }

    @Test
    fun `stream url authenticates with ApiKey, not api_key`() {
        val path = authenticationManager.buildJellyfinPath("item789", credentials, maxBitrateKbps = null)!!

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
        credentialStore.authenticatedCredentials = credentials
        server.respond("/Users/Me", "me.json")

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission sends the session's token`() {
        credentialStore.authenticatedCredentials = credentials
        server.respond("/Users/Me", "me.json")

        runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        server.requestsTo("/Users/Me").single().headers[HttpHeaders.Authorization]!! shouldContain "Token=\"token123\""
    }

    @Test
    fun `refreshDownloadPermission keeps the cached permission when the request fails`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        server.respond("/Users/Me", code = 500)

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, downloadableCredentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission signs out when the server rejects the session`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        server.respond("/Users/Me", code = 401)

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, downloadableCredentials) }

        refreshed.shouldBeNull()
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    @Test
    fun `disableDownloadPermission persists that the user can't use the Download endpoint`() {
        credentialStore.authenticatedCredentials = downloadableCredentials

        authenticationManager.disableDownloadPermission()

        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe false
    }

    @Test
    fun `a failed sign-in keeps the working session`() {
        credentialStore.authenticatedCredentials = credentials
        server.respond("/Users/AuthenticateByName", code = 401, method = "POST")

        val result = runBlocking { authenticationManager.authenticate(server.address, LoginCredentials("listener", "mistyped")) }

        result.isFailure shouldBe true
        authenticationManager.getAuthenticatedCredentials() shouldBe credentials
    }

    @Test
    fun `refreshDownloadPermission never brings back a session cleared while it ran`() {
        credentialStore.authenticatedCredentials = null
        server.respond("/Users/Me", "me.json")

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    @Test
    fun `refreshDownloadPermission never overwrites a newer sign-in`() {
        val newer = AuthenticatedCredentials(accessToken = "token-2", userId = "user456")
        credentialStore.authenticatedCredentials = newer
        server.respond("/Users/Me", "me.json")

        runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        authenticationManager.getAuthenticatedCredentials() shouldBe newer
    }
}
