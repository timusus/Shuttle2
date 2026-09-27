package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class EmbyAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val server = FixtureServer("emby")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "emby").apply {
        address = "http://emby.local:8096"
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val authenticationManager = EmbyAuthenticationManager(
        userService = UserService(createHttpClient(server.engine)),
        credentialStore = credentialStore,
        clientIdentity = clientIdentity,
        streamProfile = StreamProfile.Android
    )

    @Test
    fun `stream url authenticates with api_key`() {
        val path = authenticationManager.buildEmbyPath("item789", credentials, maxBitrateKbps = null)!!

        path shouldContain "&api_key=token123"
        path shouldContain "http://emby.local:8096/emby/Audio/item789/universal?UserId=user456"
    }

    @Test
    fun `stream url carries the persisted client identity's device id`() {
        val path = authenticationManager.buildEmbyPath("item789", credentials, maxBitrateKbps = null)!!

        path shouldContain "&DeviceId=${clientIdentity.id}"
    }

    @Test
    fun `download url prefers the Download endpoint when the user can download`() {
        val path = authenticationManager.buildDownloadPath("item789", downloadableCredentials)!!

        path shouldBe "http://emby.local:8096/emby/Items/item789/Download?api_key=token123"
    }

    @Test
    fun `download url falls back to the static stream when the user can't download`() {
        val path = authenticationManager.buildDownloadPath("item789", credentials)!!

        path shouldBe "http://emby.local:8096/emby/Audio/item789/stream?static=true&api_key=token123"
        path shouldNotContain "universal"
    }

    @Test
    fun `refreshDownloadPermission picks up a permission the server granted after login`() {
        credentialStore.authenticatedCredentials = credentials
        server.respond("/emby/Users/Me", "me.json")

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission sends the session's token`() {
        credentialStore.authenticatedCredentials = credentials
        server.respond("/emby/Users/Me", "me.json")

        runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        server.requestsTo("/emby/Users/Me").single().headers["X-Emby-Token"] shouldBe "token123"
    }

    @Test
    fun `refreshDownloadPermission keeps the cached permission when the request fails`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        server.respond("/emby/Users/Me", code = 500)

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, downloadableCredentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission signs out when the server rejects the session`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        server.respond("/emby/Users/Me", code = 401)

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
        server.respond("/emby/Users/Me", "me.json")

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        refreshed!!.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    @Test
    fun `refreshDownloadPermission never overwrites a newer sign-in`() {
        val newer = AuthenticatedCredentials(accessToken = "token-2", userId = "user456")
        credentialStore.authenticatedCredentials = newer
        server.respond("/emby/Users/Me", "me.json")

        runBlocking { authenticationManager.refreshDownloadPermission(server.address, credentials) }

        authenticationManager.getAuthenticatedCredentials() shouldBe newer
    }
}
