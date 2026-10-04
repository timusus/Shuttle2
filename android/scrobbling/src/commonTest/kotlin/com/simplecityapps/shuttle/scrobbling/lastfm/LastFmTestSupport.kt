package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.networking.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.IOException

val testCredentials = LastFmCredentials(apiKey = "api-key", sharedSecret = "shared-secret")

class FakeLastFmSessionStore(session: LastFmSession? = null) : LastFmSessionStore {
    private val _session = MutableStateFlow(session)
    override val session: StateFlow<LastFmSession?> = _session

    private val _pendingToken = MutableStateFlow<String?>(null)
    override val pendingToken: StateFlow<String?> = _pendingToken

    override var lastUsername: String? = session?.username
        private set

    override fun savePendingToken(token: String?) {
        _pendingToken.value = token
    }

    override fun signIn(session: LastFmSession) {
        lastUsername = session.username
        _session.value = session
        _pendingToken.value = null
    }

    override fun signOut() {
        _session.value = null
        _pendingToken.value = null
    }
}

/** Answers Last.fm calls from a script of JSON bodies, in order, and records the form params each one sent. */
class FakeLastFmServer {
    val requests = mutableListOf<HttpRequestData>()
    private val responses = ArrayDeque<(() -> Pair<HttpStatusCode, String>)>()

    fun enqueue(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        responses.addLast { status to json }
    }

    fun enqueueOffline() {
        responses.addLast { throw IOException("offline") }
    }

    private fun engine(dispatcher: CoroutineDispatcher?) = MockEngine(
        MockEngineConfig().apply {
            dispatcher?.let { this.dispatcher = it }
            addHandler { request ->
                requests += request
                val (status, body) = (responses.removeFirstOrNull() ?: { HttpStatusCode.OK to "{}" }).invoke()
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
    )

    /** [dispatcher]: the test's own, so a test on virtual time sees each request complete (MockEngine defaults to a real one). */
    fun client(
        credentials: LastFmCredentials = testCredentials,
        dispatcher: CoroutineDispatcher? = null
    ) = LastFmClient(LastFmApi(createHttpClient(engine(dispatcher))), credentials)

    fun form(index: Int = 0): Parameters = (requests[index].body as FormDataContent).formData
}
