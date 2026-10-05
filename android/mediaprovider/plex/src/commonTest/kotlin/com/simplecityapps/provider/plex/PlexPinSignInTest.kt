package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.AccountServer
import com.simplecityapps.mediaprovider.server.AuthenticatedCredentials
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerConnection
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.PLEX_PLATFORM
import com.simplecityapps.provider.plex.http.UserService
import com.simplecityapps.provider.plex.http.plexClientHeaders
import com.simplecityapps.provider.plex.http.sendPlexClientHeaders
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** Signing in with a plex.tv PIN, then to one of the account's servers, on a client configured as the app's. */
class PlexPinSignInTest {
    private val server = FixtureServer("plex")

    private val clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.24", deviceName = "Pixel")

    private val client = createHttpClient(server.engine) { sendPlexClientHeaders(plexClientHeaders(clientIdentity)) }

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "plex")

    private val authenticationManager = PlexAuthenticationManager(UserService(client), credentialStore, clientIdentity)

    @AfterTest
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a pin is a short one - with the web sign-in for this client and plex tv link`() = runTest {
        server.respond(PINS, "pin.json", method = "POST")

        val pin = authenticationManager.createPin().getOrThrow()

        server.requestsTo(PINS).single().url.parameters["strong"] shouldBe "false"
        pin.id shouldBe 1234567890L
        pin.code shouldBe "ABCD"
        pin.expiresInSeconds shouldBe 900
        pin.linkUrl shouldBe "https://plex.tv/link"
        pin.authUrl shouldStartWith "https://app.plex.tv/auth#?"
        pin.authUrl shouldContain "clientID=device-1"
        pin.authUrl shouldContain "code=ABCD"
        pin.authUrl shouldContain "context%5Bdevice%5D%5Bproduct%5D=Shuttle2.0"
    }

    @Test
    fun `a pin waiting for approval has no token - then the account's once approved`() = runTest {
        server.respond(PINS, "pin.json", method = "POST")
        val pin = authenticationManager.createPin().getOrThrow()

        server.respond("$PINS/1234567890", "pin.json")
        authenticationManager.checkPin(pin).getOrThrow().shouldBeNull()

        server.respond("$PINS/1234567890", "pin_approved.json")
        authenticationManager.checkPin(pin).getOrThrow() shouldBe "account-token"
        server.requestsTo("$PINS/1234567890").last().url.parameters["code"] shouldBe "ABCD"
    }

    @Test
    fun `a failed pin check fails`() = runTest {
        server.respond(PINS, "pin.json", method = "POST")
        val pin = authenticationManager.createPin().getOrThrow()
        server.respond("$PINS/1234567890", code = 404)

        authenticationManager.checkPin(pin).isFailure shouldBe true
    }

    @Test
    fun `the account's servers are its resources that provide a server - each with its own token`() = runTest {
        server.respond(RESOURCES, "resources.json")

        val servers = authenticationManager.servers("account-token").getOrThrow()

        servers.map { it.id } shouldContainExactly listOf("server-shared", "server-home")
        val home = servers.single { it.id == "server-home" }
        home.name shouldBe "Home"
        home.owned shouldBe true
        home.accessToken shouldBe "home-token"
        home.connections.map { it.uri } shouldContainExactly listOf(
            "https://198-51-100-4.aaaa.plex.direct:32400",
            "https://192-168-1-20.aaaa.plex.direct:32400",
            "https://10-0-0-1.aaaa.plex.direct:8443"
        )
        val request = server.requestsTo(RESOURCES).single()
        request.headers["X-Plex-Token"] shouldBe "account-token"
        request.url.parameters["includeHttps"] shouldBe "1"
        request.url.parameters["includeRelay"] shouldBe "1"
    }

    @Test
    fun `connecting saves the first connection to answer - and the server's token as the session`() = runTest {
        credentialStore.loginCredentials = LoginCredentials("listener", "old-password")
        // Every connection answers on the fixture server, so the order decides: the local one goes first
        server.respond("/identity", "empty.json")

        authenticationManager.connect(home).getOrThrow()

        credentialStore.address shouldBe "https://192-168-1-20.aaaa.plex.direct:32400"
        credentialStore.authenticatedCredentials shouldBe AuthenticatedCredentials(accessToken = "home-token", userId = "server-home")
        credentialStore.loginCredentials.shouldBeNull()
    }

    @Test
    fun `connecting to a server none of whose connections answer fails - keeping the saved one`() = runTest {
        val working = AuthenticatedCredentials("working-token", "server-old")
        credentialStore.address = "http://old.server:32400"
        credentialStore.authenticatedCredentials = working
        server.respond("/identity", code = 503)

        authenticationManager.connect(home).isFailure shouldBe true

        credentialStore.address shouldBe "http://old.server:32400"
        credentialStore.authenticatedCredentials shouldBe working
    }

    @Test
    fun `the signed-in server is known by its id - or by the address the user typed for it`() {
        authenticationManager.isSignedInTo(home) shouldBe false

        credentialStore.address = "http://192.168.1.20:32400"
        authenticationManager.isSignedInTo(home) shouldBe true

        credentialStore.address = "http://192.168.1.21:32400"
        authenticationManager.isSignedInTo(home) shouldBe false

        credentialStore.authenticatedCredentials = AuthenticatedCredentials("home-token", "server-home")
        authenticationManager.isSignedInTo(home) shouldBe true
    }

    @Test
    fun `every request carries the client identity and asks for JSON`() = runTest {
        server.respond(PINS, "pin.json", method = "POST")
        server.respond(RESOURCES, "resources.json")
        server.respond("/library/sections", "sections.json")

        authenticationManager.createPin()
        authenticationManager.servers("account-token")
        ItemsService(client).sections(server.address, "home-token")

        for (request in server.requests) {
            request.headers["X-Plex-Client-Identifier"] shouldBe "device-1"
            request.headers["X-Plex-Product"] shouldBe "Shuttle2.0"
            request.headers["X-Plex-Version"] shouldBe "2026.09.24"
            request.headers["X-Plex-Platform"] shouldBe PLEX_PLATFORM
            request.headers["X-Plex-Device-Name"] shouldBe "Pixel"
            request.headers["Accept"]!! shouldContain "application/json"
        }
        server.requestsTo("/library/sections").single().headers["X-Plex-Token"] shouldBe "home-token"
    }

    private companion object {
        const val PINS = "/api/v2/pins"
        const val RESOURCES = "/api/v2/resources"

        val home = AccountServer(
            id = "server-home",
            name = "Home",
            owned = true,
            accessToken = "home-token",
            connections = listOf(
                ServerConnection("https://198-51-100-4.aaaa.plex.direct:32400", local = false, address = "198.51.100.4", port = 32400),
                ServerConnection("https://192-168-1-20.aaaa.plex.direct:32400", local = true, address = "192.168.1.20", port = 32400),
                ServerConnection("https://10-0-0-1.aaaa.plex.direct:8443", relay = true, address = "10.0.0.1", port = 8443)
            )
        )
    }
}
