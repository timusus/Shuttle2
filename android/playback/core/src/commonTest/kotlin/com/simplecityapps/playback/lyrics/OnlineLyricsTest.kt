package com.simplecityapps.playback.lyrics

import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class OnlineLyricsTest {
    private val requests = mutableListOf<HttpRequestData>()
    private var now = 1_000_000L
    private var enabled = true
    private val store = InMemoryKeyValueStore()

    private var responder: (HttpRequestData) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to FOUND }

    private val engine = MockEngine { request ->
        requests += request
        val (status, body) = responder(request)
        if (status == HttpStatusCode.OK) {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        } else {
            respondError(status, body, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    }

    private fun onlineLyrics() = OnlineLyrics(
        enabled = { enabled },
        cache = LyricsCache(store) { now },
        client = LrclibClient(createHttpClient(engine))
    )

    @Test
    fun `off makes no request`() = runTest {
        enabled = false

        onlineLyrics().lookup(song()) shouldBe null

        requests.size shouldBe 0
    }

    @Test
    fun `fetches by artist title album and duration`() = runTest {
        onlineLyrics().lookup(song()) shouldBe "la la\nlo lo"

        val url = requests.single().url
        url.encodedPath shouldBe "/api/get"
        url.parameters["artist_name"] shouldBe "The Artist"
        url.parameters["track_name"] shouldBe "The Song"
        url.parameters["album_name"] shouldBe "The Album"
        url.parameters["duration"] shouldBe "215"
    }

    @Test
    fun `a second lookup is answered from the cache`() = runTest {
        val lyrics = onlineLyrics()

        lyrics.lookup(song())
        lyrics.lookup(song()) shouldBe "la la\nlo lo"

        requests.size shouldBe 1
    }

    @Test
    fun `a cached lookup still works with the setting off`() = runTest {
        onlineLyrics().lookup(song())
        enabled = false

        onlineLyrics().lookup(song()) shouldBe null

        requests.size shouldBe 1
    }

    @Test
    fun `not found is cached until it expires`() = runTest {
        responder = { HttpStatusCode.NotFound to "{}" }
        val lyrics = onlineLyrics()

        lyrics.lookup(song()) shouldBe null
        val afterFirst = requests.size
        lyrics.lookup(song()) shouldBe null
        requests.size shouldBe afterFirst

        now += LyricsCache.NOT_FOUND_TTL_MS + 1
        lyrics.lookup(song()) shouldBe null
        (requests.size > afterFirst) shouldBe true
    }

    @Test
    fun `search is tried only when get finds nothing`() = runTest {
        responder = { request ->
            if (request.url.encodedPath.endsWith("/get")) HttpStatusCode.NotFound to "{}" else HttpStatusCode.OK to "[$FOUND]"
        }

        onlineLyrics().lookup(song()) shouldBe "la la\nlo lo"

        requests.map { it.url.encodedPath } shouldBe listOf("/api/get", "/api/search")
    }

    @Test
    fun `a server error is not cached`() = runTest {
        responder = { HttpStatusCode.InternalServerError to "oops" }
        val lyrics = onlineLyrics()

        lyrics.lookup(song()) shouldBe null
        responder = { HttpStatusCode.OK to FOUND }

        lyrics.lookup(song()) shouldBe "la la\nlo lo"
    }

    @Test
    fun `sends a user agent naming the app`() = runTest {
        onlineLyrics().lookup(song())

        requests.single().headers[HttpHeaders.UserAgent] shouldBe LrclibClient.USER_AGENT
    }

    private fun song() = Song(
        id = 7,
        name = "The Song",
        albumArtist = "The Album Artist",
        artists = listOf("The Artist"),
        album = "The Album",
        track = 1,
        disc = 1,
        duration = 215_400,
        date = null,
        genres = emptyList(),
        path = "/path/to/song",
        size = 1,
        mimeType = "audio/flac",
        lastModified = Instant.fromEpochSeconds(1),
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )

    private companion object {
        const val FOUND = """{"id":1,"duration":215.0,"instrumental":false,"plainLyrics":"la la\nlo lo","syncedLyrics":"[00:01.00] la la\n[00:03.50] lo lo"}"""
    }
}
