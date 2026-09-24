package com.simplecityapps.playback.chromecast

import com.simplecityapps.playback.fakes.testSong
import fi.iki.elonen.NanoHTTPD
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** What the phone's server hands a client, with and without the session's key. */
@RunWith(RobolectricTestRunner::class)
class HttpServerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val streams = CastStreams(FakeMediaInfoProvider(), EmptyCoroutineContext)

    private lateinit var localFile: File

    private lateinit var server: HttpServer

    @Before
    fun setUp() {
        localFile = folder.newFile("song1.mp3").apply { writeBytes(AUDIO) }
        val local = testSong(1, path = localFile.absolutePath).copy(size = AUDIO.size.toLong())
        val remote = remoteSong(2)
        val castService = CastService(
            RuntimeEnvironment.getApplication(),
            FakeSongRepository(listOf(local, remote)),
            FakeArtworkImageLoader(ARTWORK),
            streams
        )
        server = HttpServer(castService, streams, port = 0).apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `a remote song redirects to its stream for the session's key`() {
        val response = get("/${streams.key}/songs/2/audio")

        response.code shouldBe 307
        response.location shouldBe FakeMediaInfoProvider.remoteUrl(2)
    }

    @Test
    fun `RS-37 a remote song's stream is refused without the key`() {
        val response = get("/songs/2/audio")

        response.code shouldBe 403
        response.location.shouldBeNull()
    }

    @Test
    fun `a remote song's stream is refused for a wrong key`() {
        val response = get("/${"0".repeat(streams.key.length)}/songs/2/audio")

        response.code shouldBe 403
        response.location.shouldBeNull()
    }

    @Test
    fun `an earlier session's key is refused`() {
        val earlier = streams.key
        streams.newSession()

        val response = get("/$earlier/songs/2/audio")

        response.code shouldBe 403
        response.location.shouldBeNull()
    }

    @Test
    fun `a local song is served for the session's key`() {
        val response = get("/${streams.key}/songs/1/audio")

        response.code shouldBe 206
        response.body shouldBe AUDIO.toList()
    }

    @Test
    fun `a local song is refused without the key`() {
        val response = get("/songs/1/audio")

        response.code shouldBe 403
        response.body shouldBe "Forbidden".toByteArray().toList()
    }

    @Test
    fun `artwork is served for the session's key and refused without it`() {
        get("/${streams.key}/songs/1/artwork").body shouldBe ARTWORK.toList()
        get("/songs/1/artwork").code shouldBe 403
        get("/wrong/songs/1/artwork").code shouldBe 403
    }

    @Test
    fun `a malformed path with the key is a bad request`() {
        get("/${streams.key}/songs/two/audio").code shouldBe 400
        get("/${streams.key}/songs/2/lyrics").code shouldBe 400
        get("/${streams.key}").code shouldBe 400
    }

    private class Response(val code: Int, val location: String?, val body: List<Byte>)

    private fun get(path: String): Response {
        val connection = URL("http://127.0.0.1:${server.listeningPort}$path").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        return try {
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            Response(code, connection.getHeaderField("Location"), stream?.use { it.readBytes().toList() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val AUDIO = ByteArray(64) { it.toByte() }
        val ARTWORK = byteArrayOf(1, 2, 3)
    }
}
