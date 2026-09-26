package com.simplecityapps.playback.chromecast

import com.simplecityapps.playback.fakes.setUpFakeUriStatics
import com.simplecityapps.playback.fakes.tearDownFakeUriStatics
import com.simplecityapps.playback.fakes.testSong
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
    fun `a remote song's stream is resolved as the receiver can play it`() = runTest {
        val song = remoteSong(1)
        streams.isResolved(song) shouldBe false

        streams.resolve(listOf(song))

        provider.requests shouldBe listOf(1L to true)
        streams.isResolved(song) shouldBe true
        streams.contentType(song) shouldBe FakeMediaInfoProvider.TRANSCODED
        streams.remoteUrl(song) shouldBe FakeMediaInfoProvider.remoteUrl(1)
        provider.requests.size shouldBe 1
    }

    @Test
    fun `a local song needs nothing resolved`() = runTest {
        val song = testSong(1, mimeType = "audio/flac")

        streams.resolve(listOf(song))

        streams.isResolved(song) shouldBe true
        streams.contentType(song) shouldBe "audio/flac"
        streams.remoteUrl(song).shouldBeNull()
        provider.requests shouldBe emptyList()
    }

    @Test
    fun `a stream that fails to resolve goes as its own type and is asked for again when fetched`() = runTest {
        val song = remoteSong(1)
        provider.failing += 1L

        streams.resolve(listOf(song))

        streams.isResolved(song) shouldBe true
        streams.contentType(song) shouldBe song.mimeType
        provider.failing.clear()
        streams.remoteUrl(song) shouldBe FakeMediaInfoProvider.remoteUrl(1)
    }

    @Test
    fun `a new session forgets the streams and changes the key`() = runTest {
        val song = remoteSong(1)
        streams.resolve(listOf(song))
        val key = streams.key

        streams.newSession()

        streams.isResolved(song) shouldBe false
        streams.key shouldNotBe key
        streams.isValid(key) shouldBe false
        streams.isValid(streams.key) shouldBe true
    }
}
