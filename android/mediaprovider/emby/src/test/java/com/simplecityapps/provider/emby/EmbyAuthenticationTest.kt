package com.simplecityapps.provider.emby

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.AuthenticatedCredentials
import com.simplecityapps.provider.emby.http.AuthenticationResult
import com.simplecityapps.provider.emby.http.UserService
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.Test

class EmbyAuthenticationTest {
    private val credentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456")
    private val downloadableCredentials = AuthenticatedCredentials(accessToken = "token123", userId = "user456", canDownload = true)

    private val authenticationManager = EmbyAuthenticationManager(
        userService = object : UserService {
            override suspend fun authenticateImpl(
                url: String,
                body: Map<String, String>,
                header: String
            ): NetworkResult<AuthenticationResult> = error("not called")
        },
        credentialStore = CredentialStore(SecurePreferenceManager(FakeSharedPreferences())).apply {
            address = "http://emby.local:8096"
        }
    )

    @Test
    fun `stream url authenticates with api_key`() {
        val path = authenticationManager.buildEmbyPath("item789", credentials)!!

        path shouldContain "&api_key=token123"
        path shouldContain "http://emby.local:8096/emby/Audio/item789/universal?UserId=user456"
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
}
