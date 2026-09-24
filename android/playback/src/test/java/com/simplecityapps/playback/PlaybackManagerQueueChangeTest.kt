package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * A repeat mode change tells the playback to pre-load the next item, except when switching to
 * Repeat One (there's no "next" to preload), and a shuffle mode change pre-loads the next item of
 * the new order. Modes already set when the manager is built are not handled as changes. Clearing the queue keeps the current item when
 * playback is active, otherwise clears everything, abandoning any load in progress. Removing the
 * current item loads the next one (carrying on if it was playing), and removing any item
 * re-prepares the next one. Every queue change re-prepares the next item once, whether made through
 * the manager or on the queue directly, except where a load follows, since the load passes its own.
 */
class PlaybackManagerQueueChangeTest {
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
        runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }) }
        events.clear()
    }

    @Test
    fun `changing repeat mode to All preloads the next item`() {
        queueManager.setRepeatMode(QueueManager.RepeatMode.All)

        events shouldBe listOf("A setRepeatMode All", "A loadNext Song2")
    }

    @Test
    fun `changing repeat mode to Off preloads the next item`() {
        queueManager.setRepeatMode(QueueManager.RepeatMode.All)
        events.clear()

        queueManager.setRepeatMode(QueueManager.RepeatMode.Off)

        events shouldBe listOf("A setRepeatMode Off", "A loadNext Song2")
    }

    @Test
    fun `changing repeat mode to One does not preload`() {
        queueManager.setRepeatMode(QueueManager.RepeatMode.One)

        events shouldBe listOf("A setRepeatMode One")
    }

    @Test
    fun `a repeat mode set before the manager is built is applied once, not handled as a change`() {
        val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
        queueManager.setRepeatMode(QueueManager.RepeatMode.All)
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false) }
        val events = mutableListOf<String>()

        testPlaybackManager(exoplayerPlayback = FakePlayback("B", events = events), queueManager = queueManager)

        events shouldBe listOf("B setRepeatMode All")
    }

    @Test
    fun `turning shuffle on preloads the next item of the shuffled order`() {
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true) }

        // Prepared once the reshuffle is in place, not from the order it replaced.
        events shouldBe listOf("A loadNext ${queueManager.getNext()?.song?.name}")
    }

    @Test
    fun `turning shuffle off preloads the next item of the original order`() {
        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = true) }
        events.clear()

        runBlocking { queueManager.setShuffleMode(QueueManager.ShuffleMode.Off, reshuffle = false) }

        events shouldBe listOf("A loadNext ${queueManager.getNext()?.song?.name}")
    }

    @Test
    fun `clearQueue while playing keeps the current item and preloads nothing next`() {
        playback.state = PlaybackState.Playing

        playbackManager.clearQueue()

        queueManager.getQueue().map { it.song.id } shouldBe listOf(1L)
        events shouldBe listOf("A loadNext null")
    }

    @Test
    fun `clearQueue while paused clears the entire queue`() {
        playback.state = PlaybackState.Paused

        playbackManager.clearQueue()

        queueManager.getQueue().shouldBeEmpty()
        queueManager.getCurrentItem() shouldBe null
        events shouldBe listOf("A loadNext null")
    }

    @Test
    fun `clearQueue while a track loads abandons the load`() {
        playbackManager.skipToNext()
        events.clear()

        playbackManager.clearQueue()
        playback.completeLoad()

        events shouldBe listOf("A loadNext null")
        playbackManager.positionAnchorFlow.value.positionMs shouldBe null
    }

    @Test
    fun `removing the current item while playing loads the next item and plays it`() {
        // #292: the queue used to move on while the player stayed on the removed track.
        playback.state = PlaybackState.Playing
        val currentItem = queueManager.getCurrentItem()!!

        playbackManager.removeQueueItem(currentItem)

        events shouldBe listOf("A load Song2 seek 0")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        queueManager.getQueue().map { it.song.id } shouldBe listOf(2L, 3L)

        playback.completeLoad()

        events shouldBe listOf("A load Song2 seek 0", "A play")
    }

    @Test
    fun `removing the current item while paused loads the next item without playing it`() {
        val currentItem = queueManager.getCurrentItem()!!

        playbackManager.removeQueueItem(currentItem)
        playback.completeLoad()

        events shouldBe listOf("A load Song2 seek 0")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L
    }

    @Test
    fun `removing the current item while it loads replaces the load`() {
        playback.state = PlaybackState.Playing
        playbackManager.skipToNext()
        events.clear()

        playbackManager.removeQueueItem(queueManager.getCurrentItem()!!)
        // The superseded skip load completes first; it mustn't play the removed track.
        playback.completeLoad()

        events shouldBe listOf("A load Song3 seek 0")

        playback.completeLoad()

        events shouldBe listOf("A load Song3 seek 0", "A play")
    }

    @Test
    fun `removing the only item pauses and abandons any load in progress`() {
        runBlocking { queueManager.setQueue(listOf(createSong(1))) }
        playback.state = PlaybackState.Playing
        playbackManager.load { }
        events.clear()

        playbackManager.removeQueueItem(queueManager.getCurrentItem()!!)
        playback.completeLoad()

        // Nothing is loaded for the emptied queue, so no next item is prepared either.
        events shouldBe listOf("A pause")
        queueManager.getQueue().shouldBeEmpty()
    }

    @Test
    fun `removing a non-current item does not pause or change the current item`() {
        playback.state = PlaybackState.Playing
        val nonCurrentItem = queueManager.getQueue().last()

        playbackManager.removeQueueItem(nonCurrentItem)

        events shouldBe listOf("A loadNext Song2")
        queueManager.getCurrentItem()!!.song.id shouldBe 1L
        queueManager.getQueue().map { it.song.id } shouldBe listOf(1L, 2L)
    }

    @Test
    fun `removing the next item prepares the one after it`() {
        playbackManager.removeQueueItem(queueManager.getQueue()[1])

        events shouldBe listOf("A loadNext Song3")
    }

    @Test
    fun `a change made directly on the queue prepares the next item`() {
        queueManager.move(2, 1)

        events shouldBe listOf("A loadNext Song3")
    }

    @Test
    fun `moving an item prepares the next item once`() {
        playbackManager.moveQueueItem(2, 1)

        events shouldBe listOf("A loadNext Song3")
    }

    @Test
    fun `adding to the queue prepares the next item once`() {
        runBlocking { playbackManager.playNext(listOf(createSong(4))) }

        events shouldBe listOf("A loadNext Song4")
    }

    @Test
    fun `a skip prepares the next item through its load alone`() {
        playbackManager.skipToNext()
        playback.completeLoad()

        events shouldBe listOf("A load Song2 seek 0", "A play")
    }

    @Test
    fun `a retry after a failed load prepares the next item through its load alone`() {
        playbackManager.load { }
        playback.failLoad()
        playback.completeLoad()

        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")
    }

    @Test
    fun `a queue change during a load prepares the next item once the load completes`() {
        playbackManager.skipToNext()
        playbackManager.moveQueueItem(0, 2)

        events shouldBe listOf("A load Song2 seek 0")

        playback.completeLoad()

        events shouldBe listOf("A load Song2 seek 0", "A play", "A loadNext Song3")
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
