package com.simplecityapps.provider.emby

import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * A [MockWebServer] that answers each Emby endpoint with a JSON fixture from `src/test/resources/emby`.
 * Requests nothing was registered for get a 404, the way a server answers an unknown path.
 */
class EmbyServer : AutoCloseable {
    private class Route(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val code: Int,
        val fixture: String?
    )

    private val routes = CopyOnWriteArrayList<Route>()

    /** Every request the server received, in order. */
    val requests = CopyOnWriteArrayList<RecordedRequest>()

    private val server =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requests += request
                        val route =
                            routes.lastOrNull { route ->
                                route.method == request.method &&
                                    route.path == request.url.encodedPath &&
                                    route.query.all { (name, value) -> request.url.queryParameter(name) == value }
                            } ?: return MockResponse(code = 404)
                        return MockResponse(code = route.code, body = route.fixture?.let(::fixture).orEmpty())
                    }
                }
            start()
        }

    /** The server's address, as the user would enter it (no trailing slash). */
    val address: String = server.url("/").toString().trimEnd('/')

    /** Answers GET [path] (with at least the given [query] parameters) with [fixture], or an empty body with [code]. */
    fun respond(
        path: String,
        fixture: String? = null,
        code: Int = 200,
        method: String = "GET",
        query: Map<String, String> = emptyMap()
    ) {
        routes += Route(method, path, query, code, fixture)
    }

    /** The requests made to [path], in order. */
    fun requestsTo(path: String): List<RecordedRequest> = requests.filter { request -> request.url.encodedPath == path }

    override fun close() {
        server.close()
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResource("emby/$name")) { "No fixture emby/$name" }.readText()
}
