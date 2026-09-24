package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test

/**
 * On load failure, PlaybackManager retries with the next queue item, up to 15 attempts, unless
 * the failed item was already the last one in the queue. If every attempt fails, the queue
 * position is reset to wherever it was when load() was first called. A slow load that outlasts
 * the load timeout is neither failed nor skipped: whatever it reports later is handled as usual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerLoadTest {
    private val events = mutableListOf<String>()
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback = FakePlayback("A", events = events)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager
        )
    }

    @Test
    fun `a single load failure advances to the next queue item and retries`() {
        runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }) }
        events.clear()

        var result: Result<Boolean>? = null
        playbackManager.load { result = it }
        playback.failLoad()
        playback.completeLoad()

        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        // The success flag reports whether it loaded on the first attempt.
        result!!.getOrThrow() shouldBe false
    }

    /** A manager on its own queue and playback, driven by [dispatcher] so the load timeout fires on virtual time. */
    private inner class SlowLoadFixture {
        val events = mutableListOf<String>()
        val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
        val playback = FakePlayback("A", events = events)
        val dispatcher = UnconfinedTestDispatcher()
        val playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            appCoroutineScope = CoroutineScope(dispatcher)
        )

        init {
            runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }) }
            events.clear()
        }

        fun outlastLoadTimeout() {
            dispatcher.scheduler.advanceTimeBy(60_000)
            dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `a slow load that outlasts the timeout plays once it completes`() {
        val fixture = SlowLoadFixture()
        var result: Result<Boolean>? = null
        fixture.playbackManager.load { loaded ->
            result = loaded
            loaded.onSuccess { fixture.playbackManager.play() }
        }
        fixture.outlastLoadTimeout()

        result shouldBe null
        fixture.queueManager.getCurrentItem()!!.song.id shouldBe 1L

        fixture.playback.completeLoad()

        result!!.getOrThrow() shouldBe true
        fixture.events.filter { it.startsWith("A load") || it == "A play" } shouldBe listOf("A load Song1 seek 0", "A play")
    }

    @Test
    fun `a slow load that fails after the timeout is retried with the next queue item`() {
        val fixture = SlowLoadFixture()
        var result: Result<Boolean>? = null
        fixture.playbackManager.load { result = it }
        fixture.outlastLoadTimeout()

        fixture.playback.failLoad()

        fixture.events.filter { it.startsWith("A load") } shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")
        fixture.queueManager.getCurrentItem()!!.song.id shouldBe 2L

        fixture.playback.completeLoad()

        result!!.getOrThrow() shouldBe false
    }

    @Test
    fun `load stops retrying after 15 attempts and reverts to the original queue position`() {
        // Pins current behaviour; see #250
        runBlocking { queueManager.setQueue((1L..20L).map { createSong(it) }) }
        events.clear()
        val originalItem = queueManager.getCurrentItem()

        var result: Result<Boolean>? = null
        playbackManager.load { result = it }
        repeat(15) { playback.failLoad() }

        events.count { it.startsWith("A load ") } shouldBe 15
        result!!.isFailure shouldBe true
        // Every attempt skipped forward, but the final failure resets the queue position back to
        // the item that was current when load() was first called.
        queueManager.getCurrentItem() shouldBe originalItem
    }

    @Test
    fun `load does not retry when the failed item is already the last in the queue`() {
        runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }) }
        queueManager.skipTo(2)
        events.clear()

        var result: Result<Boolean>? = null
        playbackManager.load { result = it }
        playback.failLoad()

        events shouldBe listOf("A load Song3 seek 0")
        result!!.isFailure shouldBe true
        queueManager.getCurrentItem()!!.song.id shouldBe 3L
    }

    private fun createSong(id: Long) = Song(
        id = id,
        name = "Song$id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/song$id.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
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
}
