package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.Policy
import com.simplecityapps.provider.emby.http.User
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EmbyAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private lateinit var meResult: NetworkResult<User>

    private val credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
        address = "http://emby.local:8096"
    }

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

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
            ): NetworkResult<User> = meResult
        },
        credentialStore = credentialStore,
        clientIdentity = clientIdentity
    )

    @Test
    fun `stream url authenticates with api_key`() {
        val path = authenticationManager.buildEmbyPath("item789", credentials)!!

        path shouldContain "&api_key=token123"
        path shouldContain "http://emby.local:8096/emby/Audio/item789/universal?UserId=user456"
    }

    @Test
    fun `stream url carries the persisted client identity's device id`() {
        val path = authenticationManager.buildEmbyPath("item789", credentials)!!

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
        meResult = NetworkResult.Success(User(id = "user456", name = "User", policy = Policy(enableContentDownloading = true)))

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission("http://emby.local:8096", credentials) }

        refreshed.canDownload shouldBe true
        authenticationManager.getAuthenticatedCredentials()!!.canDownload shouldBe true
    }

    @Test
    fun `refreshDownloadPermission keeps the cached permission when the request fails`() {
        credentialStore.authenticatedCredentials = downloadableCredentials
        meResult = NetworkResult.Failure(RuntimeException("network error"))

        val refreshed = runBlocking { authenticationManager.refreshDownloadPermission("http://emby.local:8096", downloadableCredentials) }

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
