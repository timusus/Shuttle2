package com.simplecityapps.playback.chromecast

import com.simplecityapps.playback.fakes.setUpFakeUriStatics
import com.simplecityapps.playback.fakes.tearDownFakeUriStatics
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** How a Cast receiver's streams are resolved and kept. */
class CastStreamsTest {
    @Before
    fun setUp() = setUpFakeUriStatics()

    @After
    fun tearDown() = tearDownFakeUriStatics()

    private val provider = FakeMediaInfoProvider()

    private val streams = CastStreams(provider, EmptyCoroutineContext)

    @Test
    fun `a remote entry's stream is resolved as the receiver can play it, under the entry's play id`() = runTest {
        val entry = QueueEntry(uid = 42, song = remoteSong(1))
        streams.isResolved(entry) shouldBe false

        streams.resolve(listOf(entry))

        provider.requests shouldBe listOf(1L to true)
        streams.isResolved(entry) shouldBe true
        streams.contentType(entry) shouldBe FakeMediaInfoProvider.TRANSCODED
        streams.remoteUrl(entry) shouldBe "https://media.example/1?ApiKey=secret-token&PlaySessionId=42"
        streams.resolvedUrl(42) shouldBe "https://media.example/1?ApiKey=secret-token&PlaySessionId=42"
        provider.requests.size shouldBe 1
    }

    @Test
    fun `the same song twice in the queue streams under two play ids`() = runTest {
        val song = remoteSong(1)
        val first = QueueEntry(uid = 41, song = song)
        val second = QueueEntry(uid = 42, song = song)

        streams.resolve(listOf(first, second))

        provider.requests shouldBe listOf(1L to true, 1L to true)
        streams.resolvedUrl(41) shouldBe "https://media.example/1?ApiKey=secret-token&PlaySessionId=41"
        streams.resolvedUrl(42) shouldBe "https://media.example/1?ApiKey=secret-token&PlaySessionId=42"
    }

    @Test
    fun `a local song needs nothing resolved`() = runTest {
        val entry = QueueEntry(uid = 1, song = testSong(1, mimeType = "audio/flac"))

        streams.resolve(listOf(entry))

        streams.isResolved(entry) shouldBe true
        streams.contentType(entry) shouldBe "audio/flac"
        streams.remoteUrl(entry).shouldBeNull()
        provider.requests shouldBe emptyList()
    }

    @Test
    fun `a stream that fails to resolve goes as its own type and is asked for again when fetched`() = runTest {
        val entry = QueueEntry(uid = 7, song = remoteSong(1))
        provider.failing += 1L

        streams.resolve(listOf(entry))

        streams.isResolved(entry) shouldBe true
        streams.contentType(entry) shouldBe entry.song.mimeType
        provider.failing.clear()
        streams.remoteUrl(entry) shouldBe "https://media.example/1?ApiKey=secret-token&PlaySessionId=7"
    }

    @Test
    fun `only the entries kept are remembered`() = runTest {
        val kept = QueueEntry(uid = 1, song = remoteSong(1))
        val dropped = QueueEntry(uid = 2, song = remoteSong(2))
        streams.resolve(listOf(kept, dropped))

        streams.retainOnly(listOf(1L))

        streams.isResolved(kept) shouldBe true
        streams.isResolved(dropped) shouldBe false
    }

    @Test
    fun `a new session forgets the streams and changes the key`() = runTest {
        val entry = QueueEntry(uid = 1, song = remoteSong(1))
        streams.resolve(listOf(entry))
        val key = streams.key

        streams.newSession()

        streams.isResolved(entry) shouldBe false
        streams.key shouldNotBe key
        streams.isValid(key) shouldBe false
        streams.isValid(streams.key) shouldBe true
    }
}
