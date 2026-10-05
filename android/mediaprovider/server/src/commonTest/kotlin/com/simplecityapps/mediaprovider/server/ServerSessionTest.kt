package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SecurePreferenceManager
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class ServerSessionTest {
    private val strings =
        object : ServerStrings {
            override val addressMissing = "No address"
            override val authenticationError = "Sign-in failed"
            override val musicLibraryMissing = "No music library"
            override val unknownName = "Unknown"
        }
    private val authenticatedAt = mutableListOf<String>()
    private val store = ServerCredentialStore(SecurePreferenceManager(InMemoryKeyValueStore()), "test")

    private val querying = Event.Progress(MessageProgress(ImportPhase.Connecting, progress = null))

    private fun session(
        address: String?,
        credentials: String?
    ) = withServerSession<String, String>(
        strings = strings,
        credentialStore = store,
        address = address,
        authenticate = {
            authenticatedAt += it
            credentials
        }
    ) { address, session -> emit(FlowEvent.Success("$address as ${session.credentials}")) }

    @Test
    fun `no address fails without signing in`() = runTest {
        session(address = null, credentials = "token").toList().described() shouldBe listOf(Event.Failure(strings.addressMissing))
        authenticatedAt shouldBe emptyList()
    }

    @Test
    fun `reports progress - signs in and runs the body with the session`() = runTest {
        session(address = "https://server", credentials = "token").toList().described() shouldBe listOf(
            querying,
            Event.Success("https://server as token")
        )
        authenticatedAt shouldBe listOf("https://server")
    }

    @Test
    fun `a failed sign-in fails the sync`() = runTest {
        session(address = "https://server", credentials = null).toList().described() shouldBe listOf(
            querying,
            Event.Failure(strings.authenticationError)
        )
    }

    // Signing in again after a 401 (#844)

    private val signIns = mutableListOf<String>()

    /** A session signed in as token-1, whose sign-ins hand out each of [tokens] in turn. */
    private fun signedIn(vararg tokens: String?): ServerSession<String> {
        val next = tokens.iterator()
        return ServerSession("token-1") { next.next().also { token -> signIns += token.toString() } }
    }

    private val requestsMade = mutableListOf<String>()

    /** A server that rejects [rejected] tokens with a 401 and answers anything else with the token. */
    private fun server(vararg rejected: String): suspend (String) -> NetworkResult<String> = { token ->
        requestsMade += token
        yield()
        if (token in rejected) NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Unauthorized)) else NetworkResult.Success(token)
    }

    @Test
    fun `a request the server accepts is made once - without signing in again`() = runTest {
        signedIn().request(server()) shouldBe NetworkResult.Success("token-1")

        requestsMade shouldBe listOf("token-1")
    }

    @Test
    fun `a 401 signs in again and repeats the request with the new session`() = runTest {
        val session = signedIn("token-2")

        session.request(server("token-1")) shouldBe NetworkResult.Success("token-2")

        requestsMade shouldBe listOf("token-1", "token-2")
        session.credentials shouldBe "token-2"
    }

    @Test
    fun `a session signs in again once - a later 401 is the result`() = runTest {
        val session = signedIn("token-2", "token-3")
        session.request(server("token-1"))

        ((session.request(server("token-1", "token-2")) as NetworkResult.Failure).error as RemoteServiceHttpError).httpStatusCode shouldBe HttpStatusCode.Unauthorized

        signIns shouldBe listOf("token-2")
    }

    @Test
    fun `requests rejected together sign in again once - and each is repeated with the new session`() = runTest {
        val session = signedIn("token-2")
        val fetch = server("token-1")

        val results = List(3) { async { session.request(fetch) } }.awaitAll()

        results shouldBe List(3) { NetworkResult.Success("token-2") }
        signIns shouldBe listOf("token-2")
    }

    @Test
    fun `a 401 with no new sign-in is the result`() = runTest {
        val session = signedIn(null)

        (session.request(server("token-1")) is NetworkResult.Failure) shouldBe true

        requestsMade shouldBe listOf("token-1")
        session.credentials shouldBe "token-1"
    }

    @Test
    fun `a sign-in that hands back the rejected session isn't repeated`() = runTest {
        signedIn("token-1").request(server("token-1"))

        requestsMade shouldBe listOf("token-1")
    }

    @Test
    fun `a failure other than a 401 doesn't sign in again`() = runTest {
        val session = signedIn("token-2")

        session.request { _: String -> NetworkResult.Failure(RemoteServiceHttpError(HttpStatusCode.Forbidden)) }

        signIns shouldBe emptyList()
    }
}
