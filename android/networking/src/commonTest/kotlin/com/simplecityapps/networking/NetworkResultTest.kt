package com.simplecityapps.networking

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.retrofit.error.UnexpectedError
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

class NetworkResultTest {
    @Serializable
    data class User(val name: String)

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(
        connected: Boolean = true,
        respond: MockEngine
    ) = createHttpClient(respond, connectivity = { connected })

    private fun responding(
        status: HttpStatusCode,
        body: String = ""
    ) = MockEngine { respond(body, status, json) }

    private fun throwing(throwable: Throwable) = MockEngine { throw throwable }

    private fun NetworkResult<*>.error() = (this as NetworkResult.Failure).error

    @Test
    fun `a 2xx decodes the body, ignoring keys it doesn't know`() = runTest {
        val result = client(respond = responding(HttpStatusCode.OK, """{"name":"tim","added":"later"}"""))
            .networkResult<User> { get("https://server/user") }

        result shouldBe NetworkResult.Success(User("tim"))
    }

    @Test
    fun `a 204 with no body is a success for Unit`() = runTest {
        val result = client(respond = responding(HttpStatusCode.NoContent)).networkResult<Unit> { get("https://server/report") }

        result shouldBe NetworkResult.Success(Unit)
    }

    @Test
    fun `a 401 is a client error carrying its status and body`() = runTest {
        val error = client(respond = responding(HttpStatusCode.Unauthorized, "token expired"))
            .networkResult<User> { get("https://server/user") }
            .error()

        error.shouldBeInstanceOf<RemoteServiceHttpError>()
        error.httpStatusCode shouldBe HttpStatusCode.Unauthorized
        error.body shouldBe "token expired"
        error.isClientError shouldBe true
        error.userDescription() shouldBe "An error occurred. (401)"
    }

    @Test
    fun `a 5xx is a server error`() = runTest {
        val error = client(respond = responding(HttpStatusCode.ServiceUnavailable)).networkResult<User> { get("https://server/user") }.error()

        error.shouldBeInstanceOf<RemoteServiceHttpError>()
        error.isServerError shouldBe true
        error.body shouldBe null
        error.userDescription() shouldBe "A server error occurred. (503)"
    }

    @Test
    fun `an IOException is a network error, telling offline from unreachable`() = runTest {
        val unreachable = client(connected = true, respond = throwing(IOException("refused"))).networkResult<User> { get("https://server/user") }.error()
        val offline = client(connected = false, respond = throwing(IOException("no route"))).networkResult<User> { get("https://server/user") }.error()

        unreachable.shouldBeInstanceOf<NetworkError>()
        unreachable.hasInternetConnectivity shouldBe true
        unreachable.userDescription() shouldBe "The server could not be reached."
        offline.userDescription() shouldBe "You are not connected to the internet."
    }

    @Test
    fun `a client without connectivity reports offline`() = runTest {
        val error = createHttpClient(throwing(IOException("refused"))).networkResult<User> { get("https://server/user") }.error()

        (error as NetworkError).hasInternetConnectivity shouldBe false
    }

    @Test
    fun `a body that doesn't decode is an unexpected error`() = runTest {
        val error = client(respond = responding(HttpStatusCode.OK, """{"id":1}""")).networkResult<User> { get("https://server/user") }.error()

        error.shouldBeInstanceOf<UnexpectedError>()
        error.userDescription() shouldBe "An unexpected error occurred."
    }

    @Test
    fun `a client that expects success still maps a 404 to its status`() = runTest {
        val error = createHttpClient(responding(HttpStatusCode.NotFound)) { expectSuccess = true }
            .networkResult<User> { get("https://server/user") }
            .error()

        (error as RemoteServiceHttpError).httpStatusCode shouldBe HttpStatusCode.NotFound
    }
}
