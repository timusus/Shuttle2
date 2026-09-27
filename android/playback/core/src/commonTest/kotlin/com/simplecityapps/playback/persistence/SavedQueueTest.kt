package com.simplecityapps.playback.persistence

import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.song
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/** The saved queue's format and rules, which Android's `QueueStore` and iOS's `IosPlaybackStore` both save and restore by. */
class SavedQueueTest {
    private val manager = PlaybackPreferenceManager(InMemoryKeyValueStore())
    private val writer = SavedQueueWriter(manager)

    private val a = song(1)
    private val b = song(2)
    private val c = song(3)

    /** A file opened from another app: not in the library. */
    private val opened = song(-7)

    private fun lookUp(vararg songs: Song): suspend (List<Long>) -> Map<Long, Song> = { ids -> songs.filter { it.id in ids }.associateBy { it.id } }

    @Test
    fun `the queue is saved in both orders as comma separated ids leaving out songs not in the library`() {
        writer.saveQueue(listOf(a, opened, b, c), listOf(c, a, opened, b))

        manager.queueIds shouldBe "1,2,3"
        manager.shuffleQueueIds shouldBe "3,1,2"
    }

    @Test
    fun `a queue of songs not in the library saves no queue`() {
        writer.saveQueue(listOf(opened), listOf(opened))

        manager.queueIds.shouldBeNull()
        manager.shuffleQueueIds.shouldBeNull()
    }

    @Test
    fun `the position is saved among the library songs with the song it names`() {
        writer.savePosition(listOf(opened, a, b), 2)

        manager.queuePosition shouldBe 1
        manager.restoreQueuePositionFromStart shouldBe false
        manager.nowPlaying?.songId shouldBe b.id
    }

    @Test
    fun `a current song not in the library saves the song after it to start from the beginning`() {
        writer.savePosition(listOf(a, opened, b), 1)

        manager.queuePosition shouldBe 1
        manager.restoreQueuePositionFromStart shouldBe true
        manager.nowPlaying?.songId shouldBe b.id
    }

    @Test
    fun `no position saves none`() {
        writer.savePosition(listOf(a, b), 0)
        writer.savePosition(emptyList(), null)

        manager.queuePosition.shouldBeNull()
        manager.nowPlaying.shouldBeNull()
    }

    @Test
    fun `the saved queue reads back in both orders at its position`() = runTest {
        writer.saveQueue(listOf(a, b, c), listOf(c, a, b))

        val saved = manager.readSavedQueue(ShuffleMode.On, 1, lookUp(a, b, c)).shouldNotBeNull()

        saved.songs shouldBe listOf(a, b, c)
        saved.shuffleSongs shouldBe listOf(c, a, b)
        saved.position shouldBe 1
        saved.fromStart shouldBe false
    }

    @Test
    fun `songs gone from the library are dropped and a gone current song restores the one after it from the start`() = runTest {
        writer.saveQueue(listOf(a, b, c), listOf(a, b, c))

        val saved = manager.readSavedQueue(ShuffleMode.Off, 1, lookUp(a, c)).shouldNotBeNull()

        saved.songs shouldBe listOf(a, c)
        saved.position shouldBe 1
        saved.fromStart shouldBe true
    }

    @Test
    fun `a saved queue none of whose songs are left reads as none`() = runTest {
        writer.saveQueue(listOf(a, b), listOf(a, b))

        manager.readSavedQueue(ShuffleMode.Off, 0, lookUp()).shouldBeNull()
        PlaybackPreferenceManager(InMemoryKeyValueStore()).readSavedQueue(ShuffleMode.Off, 0, lookUp(a)).shouldBeNull()
    }

    @Test
    fun `a song resumes from the saved position or else its own start`() {
        val podcast = song(4, path = "/podcasts/4.mp3", playbackPosition = 60_000)

        manager.resumePosition(podcast) shouldBe 60_000 - 5_000
        manager.resumePosition(a) shouldBe 0
        manager.playbackPosition = 1234
        manager.resumePosition(podcast) shouldBe 1234
    }
}
