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
 * position is reset to wherever it was when load() was first called. A load that never reports
 * fails once it times out, and is retried the same way.
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

    @Test
    fun `a load that never reports is retried with the next queue item once it times out`() {
        // Its own queue and playback: the manager built in setUp would also follow them.
        val events = mutableListOf<String>()
        val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
        val playback = FakePlayback("A", events = events)
        val dispatcher = UnconfinedTestDispatcher()
        val playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            appCoroutineScope = CoroutineScope(dispatcher)
        )
        runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }) }
        events.clear()

        var result: Result<Boolean>? = null
        playbackManager.load { result = it }
        dispatcher.scheduler.advanceTimeBy(30_000)
        dispatcher.scheduler.runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L

        playback.completeLoad() // Song1, reported too late
        result shouldBe null

        playback.completeLoad()

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
