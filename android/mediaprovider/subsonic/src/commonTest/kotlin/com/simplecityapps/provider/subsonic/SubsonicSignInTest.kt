package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.EXTENSIONS
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.PING
import com.simplecityapps.provider.subsonic.TestSubsonic.Companion.STAR
import com.simplecityapps.provider.subsonic.http.SubsonicError
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** Signing in with `ping`, recording what the server supports, and the fallbacks for servers that refuse tokens. */
class SubsonicSignInTest {
    private val subsonic = TestSubsonic()
    private val server = subsonic.server
    private val authenticationManager = subsonic.authenticationManager

    @AfterTest
    fun tearDown() = subsonic.close()

    @Test
    fun `signing in to an OpenSubsonic server records what it supports`() {
        subsonic.signIn()

        authenticationManager.serverInfo shouldBe SubsonicServerInfo(
            openSubsonic = true,
            type = "navidrome",
            serverVersion = "0.64.2 (10114574)",
            extensions = setOf("transcodeOffset", "formPost", "songLyrics", "indexBasedQueue", "transcoding", "playbackReport", "topSongsByArtistId")
        )
        authenticationManager.getAuthenticatedCredentials()?.userId shouldBe TestSubsonic.USERNAME
        authenticationManager.getAuthenticatedCredentials()?.accessToken shouldBe TestSubsonic.PASSWORD
    }

    @Test
    fun `the ping is signed with a salted token, never the password`() {
        server.respond(PING, "ping.json")
        server.respond(EXTENSIONS, "extensions.json")

        runBlocking { authenticationManager.authenticate(server.address, LoginCredentials(TestSubsonic.USERNAME, TestSubsonic.PASSWORD)) }

        val ping = server.requestsTo(PING).single()
        ping.parameter("u") shouldBe TestSubsonic.USERNAME
        ping.parameter("t") shouldBe md5Hex(TestSubsonic.PASSWORD + ping.parameter("s"))
        ping.parameter("p").shouldBeNull()
        ping.parameter("f") shouldBe "json"
        ping.parameter("v") shouldBe "1.16.1"
        ping.parameter("c") shouldBe "Shuttle"
    }

    @Test
    fun `a plain Subsonic server isn't asked for extensions`() {
        subsonic.signIn(ping = "ping_subsonic.json")

        authenticationManager.serverInfo?.openSubsonic shouldBe false
        authenticationManager.serverInfo?.extensions?.shouldBeEmpty()
    }

    @Test
    fun `wrong credentials fail with the server's message, and store nothing`() {
        server.respond(PING, "error_wrong_credentials.json")
        val authentication = SubsonicServerAuthentication(authenticationManager)

        val result = runBlocking { authentication.authenticate(ServerLogin(server.address, TestSubsonic.USERNAME, "wrong")) }

        result.exceptionOrNull()?.message shouldBe "Wrong username or password"
        result.exceptionOrNull()?.cause.shouldBeInstanceOf<SubsonicError.WrongCredentials>()
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }

    @Test
    fun `a server that refuses tokens is signed in to with the password, and sent it from then on`() {
        server.respond(PING, "error_token_not_supported.json")
        server.respond(PING, "ping_subsonic.json", where = { it.parameter("p") != null })
        server.respond(STAR, "ok.json", where = { it.parameter("p") != null })

        runBlocking { authenticationManager.authenticate(server.address, LoginCredentials(TestSubsonic.USERNAME, TestSubsonic.PASSWORD)).getOrThrow() }
        authenticationManager.setAddress(server.address)
        val star = runBlocking { authenticationManager.request { address, auth -> subsonic.service.star(address, auth, "song-1") } }

        server.requestsTo(PING).map { it.parameter("p") } shouldBe listOf(null, "enc:736573616d65")
        star.shouldBeInstanceOf<NetworkResult.Success<*>>()
        server.requestsTo(STAR).single().parameter("t").shouldBeNull()
    }

    @Test
    fun `a request refused a token is made again with the password`() {
        subsonic.signIn()
        server.respond(STAR, "error_token_not_supported.json")
        server.respond(STAR, "ok.json", where = { it.parameter("p") != null })

        val star = runBlocking { authenticationManager.request { address, auth -> subsonic.service.star(address, auth, "song-1") } }

        star.shouldBeInstanceOf<NetworkResult.Success<*>>()
        server.requestsTo(STAR).map { it.parameter("p") } shouldBe listOf(null, "enc:736573616d65")
    }

    @Test
    fun `with no username the password is an API key`() {
        server.respond(PING, "ping.json", where = { it.parameter("apiKey") == "key-123" && it.parameter("u") == null })
        server.respond(EXTENSIONS, "extensions_api_key.json")

        runBlocking { authenticationManager.authenticate(server.address, LoginCredentials("", "key-123")).getOrThrow() }

        authenticationManager.serverInfo?.supports(SubsonicServerInfo.API_KEY_AUTHENTICATION) shouldBe true
        authenticationManager.credentials(authenticationManager.getAuthenticatedCredentials()!!) shouldBe SubsonicCredentials.ApiKey("key-123")
    }

    @Test
    fun `a rejection mid-session signs out`() {
        subsonic.signIn()
        server.respond(STAR, "error_wrong_credentials.json")

        val star = runBlocking { authenticationManager.request { address, auth -> subsonic.service.star(address, auth, "song-1") } }

        (star as NetworkResult.Failure).error.shouldBeInstanceOf<SubsonicError.WrongCredentials>()
        authenticationManager.getAuthenticatedCredentials().shouldBeNull()
    }
}
