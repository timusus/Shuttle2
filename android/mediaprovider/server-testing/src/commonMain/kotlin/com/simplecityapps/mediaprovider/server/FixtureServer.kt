package com.simplecityapps.mediaprovider.server

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

/**
 * A fake media server on Ktor's [MockEngine], which answers each endpoint with a JSON fixture read by [loadFixture]
 * (by file name). Requests nothing was registered for get a 404, the way a server answers an unknown path.
 */
@OptIn(ExperimentalAtomicApi::class)
class FixtureServer(private val loadFixture: (name: String) -> String) : AutoCloseable {
    private class Route(
        val method: HttpMethod,
        val path: String,
        val query: Map<String, String>,
        val code: Int,
        val fixture: String?
    )

    private val routes = AtomicReference(emptyList<Route>())
    private val received = AtomicReference(emptyList<HttpRequestData>())

    /** Answers every request a client built on it makes, as the server. */
    val engine: MockEngine =
        MockEngine { request ->
            received.update { requests -> requests + request }
            val route =
                routes.load().lastOrNull { route ->
                    route.method == request.method &&
                        route.path == request.url.encodedPath &&
                        route.query.all { (name, value) -> request.url.parameters[name] == value }
                } ?: return@MockEngine respond("", HttpStatusCode.NotFound)
            respond(
                content = route.fixture?.let(loadFixture).orEmpty(),
                status = HttpStatusCode.fromValue(route.code),
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

    /** The server's address, as the user would enter it (no trailing slash). */
    val address: String = "http://fixture.server"

    /** Every request the server received, in order. */
    val requests: List<HttpRequestData> get() = received.load()

    /** Answers GET [path] (with at least the given [query] parameters) with [fixture], or an empty body with [code]. */
    fun respond(
        path: String,
        fixture: String? = null,
        code: Int = 200,
        method: String = "GET",
        query: Map<String, String> = emptyMap()
    ) {
        routes.update { routes -> routes + Route(HttpMethod.parse(method), path, query, code, fixture) }
    }

    /** The requests made to [path], in order. */
    fun requestsTo(path: String): List<HttpRequestData> = requests.filter { request -> request.url.encodedPath == path }

    /** Forgets the requests received so far. */
    fun clearRequests() {
        received.store(emptyList())
    }

    override fun close() {
        engine.close()
    }
}

/** The request's body as text (the JSON a client sent, say); empty when it sent none. */
val HttpRequestData.bodyText: String
    get() =
        when (val content = body) {
            is TextContent -> content.text
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> ""
        }
