package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.networking.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class FakeListenBrainzSessionStore(account: ListenBrainzAccount? = null) : ListenBrainzSessionStore {
    private val _account = MutableStateFlow(account)
    override val account: StateFlow<ListenBrainzAccount?> = _account

    override var lastUsername: String? = account?.username
        private set

    override fun signIn(account: ListenBrainzAccount) {
        lastUsername = account.username
        _account.value = account
    }

    override fun signOut() {
        _account.value = null
    }
}

/** Answers ListenBrainz calls from a script of JSON bodies, in order, and records each request. */
class FakeListenBrainzServer {
    val requests = mutableListOf<HttpRequestData>()
    private val responses = ArrayDeque<(() -> Pair<HttpStatusCode, String>)>()

    fun enqueue(
        json: String = """{"status":"ok"}""",
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
                val (status, body) = (responses.removeFirstOrNull() ?: { HttpStatusCode.OK to """{"status":"ok"}""" }).invoke()
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
    )

    /** [dispatcher]: the test's own, so a test on virtual time sees each request complete (MockEngine defaults to a real one). */
    fun client(dispatcher: CoroutineDispatcher? = null) = ListenBrainzClient(ListenBrainzApi(createHttpClient(engine(dispatcher))))

    fun body(index: Int = 0): JsonObject = Json.parseToJsonElement((requests[index].body as TextContent).text).jsonObject
}
