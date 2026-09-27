package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FakeSharedPreferences
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.provider.plex.http.plexClientHeaders
import com.simplecityapps.provider.plex.http.sendPlexClientHeaders
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** Signing in through plex.tv, on a client configured as the app's is: every request carries the client identity. */
class PlexSignInTest {
    private val server = FixtureServer("plex")

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val client = createHttpClient(server.engine) { sendPlexClientHeaders(plexClientHeaders(clientIdentity)) }

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(FakeSharedPreferences()), "plex").apply {
        address = server.address
    }

    private val authenticationManager = PlexAuthenticationManager(UserService(client), credentialStore, clientIdentity)

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `signing in posts the login to plex tv, the two-factor code after the password`() = runTest {
        server.respond(SIGN_IN, "sign_in.json", method = "POST")

        authenticationManager.authenticate(server.address, LoginCredentials("listener", "secret", authCode = "123456"))

        val request = server.requestsTo(SIGN_IN).single()
        request.url.host shouldBe "plex.tv"
        request.url.parameters["user[login]"] shouldBe "listener"
        request.url.parameters["user[password]"] shouldBe "secret123456"
    }

    @Test
    fun `a successful sign-in saves the account's token and id`() = runTest {
        server.respond(SIGN_IN, "sign_in.json", method = "POST")

        val credentials = authenticationManager.authenticate(server.address, LoginCredentials("listener", "secret")).getOrThrow()

        credentials shouldBe AuthenticatedCredentials(accessToken = "token-2", userId = "12345678")
        credentialStore.authenticatedCredentials shouldBe credentials
    }

    @Test
    fun `a failed sign-in fails`() = runTest {
        server.respond(SIGN_IN, code = 401, method = "POST")

        authenticationManager.authenticate(server.address, LoginCredentials("listener", "wrong")).isFailure shouldBe true
        credentialStore.authenticatedCredentials.shouldBeNull()
    }

    @Test
    fun `a failed sign-in keeps the working session`() = runTest {
        val working = AuthenticatedCredentials("working-token", "7")
        credentialStore.authenticatedCredentials = working
        server.respond(SIGN_IN, code = 401, method = "POST")

        authenticationManager.authenticate(server.address, LoginCredentials("listener", "mistyped")).isFailure shouldBe true

        credentialStore.authenticatedCredentials shouldBe working
    }

    @Test
    fun `every request carries the client identity and asks for JSON`() = runTest {
        server.respond(SIGN_IN, "sign_in.json", method = "POST")
        server.respond("/library/sections", "sections.json")

        authenticationManager.authenticate(server.address, LoginCredentials("listener", "secret"))
        ItemsService(client).sections(server.address, "token-2")

        for (request in server.requests) {
            request.headers["X-Plex-Client-Identifier"] shouldBe "device-1"
            request.headers["X-Plex-Product"] shouldBe "Shuttle2.0"
            request.headers["X-Plex-Version"] shouldBe "2026.09.24"
            request.headers["X-Plex-Platform"] shouldBe "Android"
            request.headers["X-Plex-Device-Name"] shouldBe "Pixel"
            request.headers["Accept"]!! shouldContain "application/json"
        }
        server.requestsTo("/library/sections").single().headers["X-Plex-Token"] shouldBe "token-2"
    }

    private companion object {
        const val SIGN_IN = "/users/sign_in"
    }
}
