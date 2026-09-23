package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
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
 * Repeat One (there's no "next" to preload). Clearing the queue keeps the current item when
 * playback is active, otherwise clears everything. Removing the current item pauses and skips
 * forward before it's removed from the queue.
 */
class PlaybackManagerQueueChangeTest {
    private val events = mutableListOf<String>()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback = FakePlayback("A", events = events)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueWatcher = queueWatcher,
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
    fun `removing the current item pauses playback and skips forward before removing it`() {
        playback.state = PlaybackState.Playing
        val currentItem = queueManager.getCurrentItem()!!

        playbackManager.removeQueueItem(currentItem)

        events shouldBe listOf("A pause")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        queueManager.getQueue().map { it.song.id } shouldBe listOf(2L, 3L)
    }

    @Test
    fun `removing a non-current item does not pause or change the current item`() {
        playback.state = PlaybackState.Playing
        val nonCurrentItem = queueManager.getQueue().last()

        playbackManager.removeQueueItem(nonCurrentItem)

        events.shouldBeEmpty()
        queueManager.getCurrentItem()!!.song.id shouldBe 1L
        queueManager.getQueue().map { it.song.id } shouldBe listOf(1L, 2L)
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
