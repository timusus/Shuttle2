package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.mediaprovider.server.StreamProfile
import com.simplecityapps.mediaprovider.server.mediabrowser.MediaBrowserServerAuthentication
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** The shared sign-in form's Jellyfin password sign-in: the saved login, and a failure's message fit for the user. */
class JellyfinServerAuthenticationTest {
    private val server = FixtureServer("jellyfin")

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "jellyfin")

    private val authentication = MediaBrowserServerAuthentication(
        JellyfinAuthenticationManager(
            httpClient = createHttpClient(server.engine),
            credentialStore = credentialStore,
            clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.27", deviceName = "Pixel"),
            streamProfile = StreamProfile.Android,
        ),
    )

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a rejected sign-in saves the address and fails with a message for the user`() {
        server.respond("/Users/AuthenticateByName", code = 401, method = "POST")

        val result = runBlocking { authentication.authenticate(ServerLogin(server.address, "listener", "mistyped")) }

        result.exceptionOrNull()?.message shouldBe "An error occurred. (401)"
        authentication.savedLogin() shouldBe SavedServerLogin(address = server.address)
    }

    @Test
    fun `a remembered login is the saved one until it's forgotten`() {
        authentication.rememberLogin(ServerLogin("http://jellyfin.local:8096", "listener", "secret"))
        credentialStore.address = "http://jellyfin.local:8096"

        authentication.savedLogin() shouldBe SavedServerLogin("http://jellyfin.local:8096", "listener", "secret")

        authentication.forgetLogin()

        authentication.savedLogin() shouldBe SavedServerLogin(address = "http://jellyfin.local:8096")
    }
}
