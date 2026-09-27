package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.server.FakeSharedPreferences
import com.simplecityapps.mediaprovider.server.FixtureServer
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.retrofit.NetworkResultAdapterFactory
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

/** Jellyfin Quick Connect sign-in (#505) against a MockWebServer serving JSON fixtures in the server's response shape. */
class JellyfinQuickConnectTest {
    private val server = FixtureServer("jellyfin")

    private val retrofit = Retrofit.Builder()
        .baseUrl("${server.address}/")
        .addCallAdapterFactory(NetworkResultAdapterFactory(null))
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()))
        .build()

    private val credentialStore = ServerCredentialStore(SecurePreferenceManager(FakeSharedPreferences()), "jellyfin")

    private val authenticationManager = JellyfinAuthenticationManager(
        userService = retrofit.create(),
        credentialStore = credentialStore,
        clientIdentity = ClientIdentity(id = "device-1", clientName = "Shuttle2.0", version = "2026.09.27", deviceName = "Pixel")
    )

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `Enabled reports what the server says`() {
        server.respond("/QuickConnect/Enabled", "quickconnect_enabled_true.json")

        runBlocking { authenticationManager.isQuickConnectEnabled(server.address) } shouldBe true
    }

    @Test
    fun `a server without Quick Connect at all reports it as unavailable`() {
        // No route registered: the fixture server answers with a 404, the way a server that predates Quick Connect would.
        runBlocking { authenticationManager.isQuickConnectEnabled(server.address) } shouldBe false
    }

    @Test
    fun `initiate posts and returns the code and secret`() {
        server.respond("/QuickConnect/Initiate", "quickconnect_initiate.json", method = "POST")

        val result = runBlocking { authenticationManager.initiateQuickConnect(server.address) }

        result.getOrThrow() shouldBe QuickConnectCode(code = "123456", secret = "secret-1")
    }

    @Test
    fun `initiate falls back to GET when an older server rejects the POST`() {
        server.respond("/QuickConnect/Initiate", code = 404, method = "POST")
        server.respond("/QuickConnect/Initiate", "quickconnect_initiate.json", method = "GET")

        val result = runBlocking { authenticationManager.initiateQuickConnect(server.address) }

        result.getOrThrow() shouldBe QuickConnectCode(code = "123456", secret = "secret-1")
        server.requestsTo("/QuickConnect/Initiate").map { it.method } shouldBe listOf("POST", "GET")
    }

    @Test
    fun `poll reports Pending until the server says the code was Authenticated`() {
        server.respond("/QuickConnect/Connect", "quickconnect_pending.json", query = mapOf("secret" to "secret-1"))

        runBlocking { authenticationManager.pollQuickConnect(server.address, "secret-1") }.getOrThrow() shouldBe QuickConnectPollState.Pending
    }

    @Test
    fun `poll reports Authenticated once approved`() {
        server.respond("/QuickConnect/Connect", "quickconnect_authenticated.json", query = mapOf("secret" to "secret-1"))

        runBlocking { authenticationManager.pollQuickConnect(server.address, "secret-1") }.getOrThrow() shouldBe QuickConnectPollState.Authenticated
    }

    @Test
    fun `poll reports Denied on a 401, Jellyfin's only signal for an expired or rejected code`() {
        server.respond("/QuickConnect/Connect", code = 401, query = mapOf("secret" to "secret-1"))

        runBlocking { authenticationManager.pollQuickConnect(server.address, "secret-1") }.getOrThrow() shouldBe QuickConnectPollState.Denied
    }

    @Test
    fun `authenticate redeems the secret for a token, stored the same way a password sign-in's is`() {
        server.respond("/Users/AuthenticateWithQuickConnect", "quickconnect_authenticate.json", method = "POST")

        val result = runBlocking { authenticationManager.authenticateWithQuickConnect(server.address, "secret-1") }

        result.getOrThrow().accessToken shouldBe "token-quickconnect"
        credentialStore.authenticatedCredentials?.accessToken shouldBe "token-quickconnect"
        credentialStore.loginCredentials shouldBe null
    }
}
